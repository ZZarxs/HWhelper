package ru.hwhelper.client.standalone;

import com.sun.jna.*;
import java.nio.file.Path;

/** TDLib's official JSON C ABI, hosted in Minecraft's JVM. */
public final class TdJson implements AutoCloseable {
    private static NativeLibrary library;
    private final Function create,send,receive,execute,destroy;private Pointer client;
    public TdJson(Path directory)throws Exception {
        synchronized(TdJson.class){if(library==null)library=NativeBinaries.loadTelegram(directory);}
        create=library.getFunction("td_json_client_create");send=library.getFunction("td_json_client_send");receive=library.getFunction("td_json_client_receive");execute=library.getFunction("td_json_client_execute");destroy=library.getFunction("td_json_client_destroy");
        execute("{\"@type\":\"setLogVerbosityLevel\",\"new_verbosity_level\":0}");
        execute("{\"@type\":\"setLogStream\",\"log_stream\":{\"@type\":\"logStreamEmpty\"}}");
    }
    public String execute(String request){return execute.invokeString(new Object[]{null,request},false);}
    public void open(){if(client==null){client=create.invokePointer(new Object[]{});if(client==null)throw new IllegalStateException("TDLib не создала клиент");}}
    public void send(String request){if(client==null)throw new IllegalStateException("Telegram не подключён");send.invokeVoid(new Object[]{client,request});}
    public String receive(double timeout){return client==null?null:receive.invokeString(new Object[]{client,timeout},false);}
    public void close(){if(client!=null){Pointer value=client;client=null;destroy.invokeVoid(new Object[]{value});}}
}
