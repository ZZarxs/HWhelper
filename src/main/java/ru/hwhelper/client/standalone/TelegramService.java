package ru.hwhelper.client.standalone;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.*;

/** Authentication and the existing @liteeventbot protocol. No localhost server or external process. */
public final class TelegramService implements AutoCloseable {
    public enum Phase { CONFIG,STARTING,PHONE,QR,CODE,PASSWORD,EMAIL,EMAIL_CODE,READY,STOPPED,ERROR }
    public record Status(Phase phase,String message,String qrLink,String hint){}
    @FunctionalInterface public interface SnapshotHandler {boolean accept(String text,long timestamp);}
    private final Path directory;private final TelegramSecrets secrets;private final SnapshotHandler snapshots;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"hwhelper-telegram");t.setDaemon(true);return t;});
    private final ConcurrentLinkedQueue<JsonObject> commands=new ConcurrentLinkedQueue<>();
    private final AtomicLong generation=new AtomicLong();private volatile boolean active;
    private volatile Status status=new Status(Phase.CONFIG,"Настройте Telegram, чтобы получать автошахты","","");
    private static final Pattern FLOOD=Pattern.compile("(?:retry after |FLOOD_WAIT_)(\\d+)",Pattern.CASE_INSENSITIVE);
    public TelegramService(Path directory,SnapshotHandler snapshots){this.directory=directory;this.secrets=new TelegramSecrets(directory);this.snapshots=snapshots;}
    public Status status(){return status;}public boolean ready(){return status.phase()==Phase.READY;}public boolean active(){return active;}public boolean credentialsSaved(){return secrets.exists();}
    public void connectSaved(){connect(0,"");}
    public synchronized void connect(int apiId,String apiHash){
        long current=generation.incrementAndGet();commands.clear();active=true;status=new Status(Phase.STARTING,"Загружаю встроенный Telegram…","","");
        executor.execute(()->{
            try{
                NativeBinaries.architecture();TelegramSecrets.Credentials credentials=apiId>0?secrets.write(apiId,apiHash):secrets.read();
                if(current==generation.get())run(current,credentials);
            }catch(Exception|LinkageError e){if(current==generation.get()){active=false;status=new Status(Phase.ERROR,friendly(e.getMessage()),"","");}}
        });
    }
    public void phone(String value){String phone=value.strip().replace(" ","").replace("-","");if(!phone.matches("\\+?[0-9]{5,20}")){showError("Введите номер телефона с кодом страны");return;}JsonObject j=function("setAuthenticationPhoneNumber");j.addProperty("phone_number",phone);commands.add(j);}
    public void qr(){JsonObject j=function("requestQrCodeAuthentication");j.add("other_user_ids",new JsonArray());commands.add(j);}
    public void code(String value){JsonObject j=function("checkAuthenticationCode");j.addProperty("code",value.strip().replace(" ",""));commands.add(j);}
    public void password(String value){JsonObject j=function("checkAuthenticationPassword");j.addProperty("password",value);commands.add(j);}
    public void email(String value){JsonObject j=function("setAuthenticationEmailAddress");j.addProperty("email_address",value.strip());commands.add(j);}
    public void emailCode(String value){JsonObject j=function("checkAuthenticationEmailCode"),code=function("emailAddressAuthenticationCode");code.addProperty("code",value.strip());j.add("code",code);commands.add(j);}
    public void logout(){commands.add(function("logOut"));}
    public void disconnect(){generation.incrementAndGet();commands.clear();active=false;status=new Status(Phase.STOPPED,"Подключение остановлено; сессия сохранена","","");}
    public void close(){disconnect();executor.shutdown();try{executor.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private void showError(String error){Status previous=status;status=new Status(previous.phase(),error,previous.qrLink(),previous.hint());}
    private void run(long current,TelegramSecrets.Credentials credentials)throws Exception {
        Files.createDirectories(directory.resolve("telegram/database"));Files.createDirectories(directory.resolve("telegram/files"));
        try(TdJson td=new TdJson(directory)){
            td.open();Worker worker=new Worker(td,credentials);
            while(current==generation.get()&&!Thread.currentThread().isInterrupted()){
                for(JsonObject command;(command=commands.poll())!=null;)worker.send(command,response->{if(type(response).equals("error"))showError(friendly(string(response,"message")));});
                String raw=td.receive(.15);if(current!=generation.get())break;
                if(raw!=null)worker.receive(JsonParser.parseString(raw).getAsJsonObject());worker.tick();
                if(worker.closed)break;
            }
            td.send(function("close").toString());long deadline=System.nanoTime()+1_000_000_000L;
            while(!worker.closed&&System.nanoTime()<deadline){
                String raw=td.receive(.1);
                if(raw!=null){JsonObject update=JsonParser.parseString(raw).getAsJsonObject();
                    if(type(update).equals("updateAuthorizationState")&&type(update.getAsJsonObject("authorization_state")).equals("authorizationStateClosed"))worker.closed=true;
                }
            }
        }finally{if(current==generation.get()){active=false;if(status.phase()!=Phase.ERROR)status=new Status(Phase.STOPPED,"Telegram отключён","","");}}
    }
    private final class Worker {
        final TdJson td;final TelegramSecrets.Credentials credentials;final Map<String,Consumer<JsonObject>> pending=new HashMap<>();
        final TelegramPollGate gate=new TelegramPollGate();long sequence,chatId,botUserId,nextLookup;boolean closed,authenticated,searching;String signature="";
        Worker(TdJson td,TelegramSecrets.Credentials credentials){this.td=td;this.credentials=credentials;}
        void send(JsonObject request,Consumer<JsonObject> callback){String id=Long.toString(++sequence);request.addProperty("@extra",id);pending.put(id,callback);td.send(request.toString());}
        void receive(JsonObject update){
            String kind=type(update);
            if(update.has("@extra")){Consumer<JsonObject> callback=pending.remove(string(update,"@extra"));if(callback!=null)callback.accept(update);return;}
            switch(kind){
                case "updateMessageSendFailed"->{if(number(update.getAsJsonObject("message"),"chat_id")==chatId)botError(update.getAsJsonObject("error"));}
                case "updateAuthorizationState"->authorization(update.getAsJsonObject("authorization_state"));
                case "updateNewMessage"->{JsonObject message=update.getAsJsonObject("message");if(number(message,"chat_id")==chatId)publish(message);}
                case "updateMessageContent","updateMessageEdited"->{if(chatId!=0&&number(update,"chat_id")==chatId){JsonObject get=function("getMessage");get.addProperty("chat_id",chatId);get.addProperty("message_id",number(update,"message_id"));send(get,this::publish);}}
                default->{}
            }
        }
        void authorization(JsonObject auth){
            authenticated=false;
            switch(type(auth)){
                case "authorizationStateWaitTdlibParameters"->{
                    JsonObject params=function("setTdlibParameters");params.addProperty("use_test_dc",false);
                    params.addProperty("database_directory",directory.resolve("telegram/database").toAbsolutePath().toString());params.addProperty("files_directory",directory.resolve("telegram/files").toAbsolutePath().toString());
                    params.addProperty("database_encryption_key",Base64.getEncoder().encodeToString(credentials.databaseKey()));
                    params.addProperty("use_file_database",false);params.addProperty("use_chat_info_database",true);params.addProperty("use_message_database",false);params.addProperty("use_secret_chats",false);
                    params.addProperty("api_id",credentials.apiId());params.addProperty("api_hash",credentials.apiHash());params.addProperty("system_language_code","ru");params.addProperty("device_model","HWhelper / "+System.getProperty("os.arch"));
                    params.addProperty("system_version",System.getProperty("os.version"));params.addProperty("application_version","3.2.1");
                    send(params,response->{if(type(response).equals("error")){status=new Status(Phase.ERROR,friendly(string(response,"message")),"","");closed=true;}});
                }
                case "authorizationStateWaitEncryptionKey"->{JsonObject key=function("checkDatabaseEncryptionKey");key.addProperty("encryption_key",Base64.getEncoder().encodeToString(credentials.databaseKey()));send(key,response->{if(type(response).equals("error")){status=new Status(Phase.ERROR,"Не удалось открыть локальную сессию Telegram","","");closed=true;}});}
                case "authorizationStateWaitPhoneNumber"->status=new Status(Phase.PHONE,"Войдите по QR или укажите номер телефона","","");
                case "authorizationStateWaitOtherDeviceConfirmation"->status=new Status(Phase.QR,"Telegram на телефоне → Устройства → Подключить устройство",string(auth,"link"),"");
                case "authorizationStateWaitCode"->status=new Status(Phase.CODE,"Введите код, который прислал Telegram","","");
                case "authorizationStateWaitPassword"->status=new Status(Phase.PASSWORD,"Введите пароль двухэтапной проверки","",string(auth,"password_hint"));
                case "authorizationStateWaitEmailAddress"->status=new Status(Phase.EMAIL,"Telegram запрашивает адрес электронной почты","","");
                case "authorizationStateWaitEmailCode"->status=new Status(Phase.EMAIL_CODE,"Введите код подтверждения электронной почты","","");
                case "authorizationStateReady"->{authenticated=true;chatId=botUserId=0;status=new Status(Phase.READY,"Подключено. Получаю снимок @liteeventbot…","","");}
                case "authorizationStateClosed"->{closed=true;}
                case "authorizationStateLoggingOut","authorizationStateClosing"->status=new Status(Phase.STOPPED,"Завершаю сессию…","","");
                case "authorizationStateWaitRegistration"->status=new Status(Phase.ERROR,"Войдите в существующую учётную запись Telegram","","");
                default->status=new Status(Phase.ERROR,"Telegram запрашивает дополнительное подтверждение. Попробуйте вход по QR.","","");
            }
        }
        void tick(){
            long now=System.currentTimeMillis();if(!authenticated||closed)return;
            if(chatId==0){
                if(searching||now<nextLookup)return;searching=true;nextLookup=now+15000;
                JsonObject lookup=function("searchPublicChat");lookup.addProperty("username","liteeventbot");send(lookup,result->{
                    searching=false;if(type(result).equals("error")){botError(result);return;}
                    chatId=number(result,"id");JsonObject type=result.getAsJsonObject("type");botUserId=type==null?0:number(type,"user_id");
                    JsonObject history=function("getChatHistory");history.addProperty("chat_id",chatId);history.addProperty("from_message_id",0);history.addProperty("offset",0);history.addProperty("limit",10);history.addProperty("only_local",false);
                    send(history,messages->{if(messages.has("messages"))for(JsonElement message:messages.getAsJsonArray("messages"))if(publish(message.getAsJsonObject()))break;});
                });return;
            }
            if(!gate.request(now))return;
            JsonObject request=function("sendMessage");request.addProperty("chat_id",chatId);
            JsonObject input=function("inputMessageText"),text=function("formattedText");text.addProperty("text","Снимок шахт");text.add("entities",new JsonArray());input.add("text",text);input.addProperty("clear_draft",false);request.add("input_message_content",input);
            send(request,response->{if(type(response).equals("error"))botError(response);});
        }
        boolean publish(JsonObject message){
            var snapshot=TelegramSnapshotProtocol.read(message,chatId,botUserId,System.currentTimeMillis());
            if(snapshot==null||snapshot.signature().equals(signature)||!snapshots.accept(snapshot.text(),snapshot.timestamp()))return false;
            signature=snapshot.signature();gate.response();status=new Status(Phase.READY,"Автошахты подключены: @liteeventbot","","");return true;
        }
        void botError(JsonObject error){String raw=string(error,"message");Matcher matcher=FLOOD.matcher(raw);int seconds=matcher.find()?Integer.parseInt(matcher.group(1)):15;gate.backoff(System.currentTimeMillis(),seconds);status=new Status(Phase.READY,"Telegram: "+friendly(raw),"","");}
    }
    static JsonObject function(String type){JsonObject json=new JsonObject();json.addProperty("@type",type);return json;}
    static String type(JsonObject json){return string(json,"@type");}
    static String string(JsonObject json,String key){try{return json.get(key).getAsString();}catch(RuntimeException e){return "";}}
    static long number(JsonObject json,String key){try{return json.get(key).getAsLong();}catch(RuntimeException e){return 0;}}
    private static String friendly(String message){
        if(message==null||message.isBlank())return "Не удалось подключить Telegram";
        if(message.contains("PHONE_CODE_INVALID"))return "Неверный код Telegram";
        if(message.contains("PHONE_CODE_EXPIRED"))return "Код истёк. Запросите новый вход";
        if(message.contains("PASSWORD_HASH_INVALID"))return "Неверный пароль двухэтапной проверки";
        if(message.contains("API_ID_INVALID")||message.contains("API_ID_PUBLISHED_FLOOD"))return "Проверьте личные API ID и API Hash с my.telegram.org/apps";
        if(message.toLowerCase(Locale.ROOT).contains("database")&&message.toLowerCase(Locale.ROOT).contains("lock"))return "Сессия уже открыта в другой копии Minecraft";
        return message.length()>180?message.substring(0,180):message;
    }
}
