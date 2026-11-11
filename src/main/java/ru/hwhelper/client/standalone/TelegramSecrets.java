package ru.hwhelper.client.standalone;

import com.google.gson.*;
import com.sun.jna.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.io.IOException;

/** API credentials and the TDLib database key are protected by the current Windows user's DPAPI. */
public final class TelegramSecrets {
    public record Credentials(int apiId,String apiHash,byte[] databaseKey){}
    private final Path file;
    public TelegramSecrets(Path directory){file=directory.resolve("telegram/credentials.dpapi");}
    public boolean exists(){return Files.isRegularFile(file);}
    public Credentials read()throws Exception {
        byte[] plain=crypt(Files.readAllBytes(file),false);
        try{
            JsonObject json=JsonParser.parseString(new String(plain,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return new Credentials(json.get("apiId").getAsInt(),json.get("apiHash").getAsString(),Base64.getDecoder().decode(json.get("databaseKey").getAsString()));
        }finally{Arrays.fill(plain,(byte)0);}
    }
    public Credentials write(int id,String hash)throws Exception {
        if(id<=0||!hash.matches("[a-fA-F0-9]{32}"))throw new IllegalArgumentException("API ID — положительное число, API Hash — 32 шестнадцатеричных символа");
        byte[] key=null;if(exists())try{key=read().databaseKey();}catch(Exception ignored){}
        if(key==null){key=new byte[32];new SecureRandom().nextBytes(key);}
        JsonObject json=new JsonObject();json.addProperty("apiId",id);json.addProperty("apiHash",hash);json.addProperty("databaseKey",Base64.getEncoder().encodeToString(key));
        byte[] plain=json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),protectedBytes;
        try{protectedBytes=crypt(plain,true);}finally{Arrays.fill(plain,(byte)0);}
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"credentials-",".tmp");
        try{Files.write(temporary,protectedBytes);Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
        return new Credentials(id,hash,key);
    }
    private static byte[] crypt(byte[] input,boolean protect)throws IOException {
        NativeBinaries.architecture();var crypt=NativeLibrary.getInstance("crypt32",WindowsNative.OPTIONS);var kernel=NativeLibrary.getInstance("kernel32",WindowsNative.OPTIONS);
        try(Memory data=new Memory(input.length);Memory in=new Memory(16);Memory out=new Memory(16)){
            data.write(0,input,0,input.length);in.clear();out.clear();in.setInt(0,input.length);in.setPointer(8,data);
            Object[] args=protect?new Object[]{in,new WString("HWhelper Telegram"),null,null,null,1,out}:new Object[]{in,null,null,null,null,1,out};
            int ok=crypt.getFunction(protect?"CryptProtectData":"CryptUnprotectData").invokeInt(args);
            if(ok==0)throw new IOException("Windows не смог расшифровать настройки Telegram для этой учётной записи");
            Pointer result=out.getPointer(8);try{return result.getByteArray(0,out.getInt(0));}finally{kernel.getFunction("LocalFree").invokePointer(new Object[]{result});}
        }
    }
}
