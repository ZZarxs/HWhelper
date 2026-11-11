package ru.hwhelper.client.standalone;

import java.nio.file.*;
import java.util.Arrays;

/** Read-only Windows integration checks. Never authorizes Telegram or sends a message. */
public final class StandaloneNativeProbe {
    public static void main(String[] args)throws Exception {
        Path root=Path.of("work/standalone/probe").toAbsolutePath();Files.createDirectories(root);
        try(TdJson td=new TdJson(root)){
            String version=td.execute("{\"@type\":\"getOption\",\"name\":\"version\"}");
            if(version==null||!version.contains("optionValueString"))throw new AssertionError("TDLib did not return its version: "+version);
            System.out.println("Embedded TDLib loaded: "+version);
            td.open();td.send("{\"@type\":\"getAuthorizationState\",\"@extra\":\"probe\"}");
            boolean received=false;long deadline=System.nanoTime()+3_000_000_000L;
            while(System.nanoTime()<deadline){String raw=td.receive(.1);if(raw!=null&&raw.contains("\"@extra\":\"probe\"")&&raw.contains("authorizationStateWaitTdlibParameters")){received=true;break;}}
            if(!received)throw new AssertionError("TDLib asynchronous JSON transport did not respond");
            td.send("{\"@type\":\"close\"}");deadline=System.nanoTime()+3_000_000_000L;boolean stopped=false;
            while(System.nanoTime()<deadline){String raw=td.receive(.1);if(raw!=null&&raw.contains("authorizationStateClosed")){stopped=true;break;}}
            if(!stopped)throw new AssertionError("TDLib did not close cleanly");
            System.out.println("Embedded TDLib create/send/receive/close passed without network or authorization");
        }
        TelegramSecrets vault=new TelegramSecrets(root);byte[] key=vault.write(12345,"00000000000000000000000000000000").databaseKey();
        var read=vault.read();if(read.apiId()!=12345||!Arrays.equals(key,read.databaseKey()))throw new AssertionError("DPAPI round trip failed");
        System.out.println("Windows DPAPI credentials round trip passed (synthetic credentials only)");
        try(WindowsMediaReader media=new WindowsMediaReader()){
            var sample=media.poll();System.out.println("Windows media: "+sample.status()+", titlePresent="+!sample.title().isBlank()+", playing="+sample.playing()+", coverBytes="+sample.cover().length);
        }
    }
}
