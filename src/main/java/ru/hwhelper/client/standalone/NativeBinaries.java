package ru.hwhelper.client.standalone;

import com.google.gson.*;
import com.sun.jna.*;
import java.nio.file.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Extracts only the pinned native libraries bundled in this JAR; never downloads or starts a process. */
public final class NativeBinaries {
    private static final List<Pointer> HANDLES=new ArrayList<>();
    public static String architecture(){
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("windows"))throw new UnsupportedOperationException("Самостоятельная версия рассчитана на Windows 10/11");
        return switch(System.getProperty("os.arch","").toLowerCase(Locale.ROOT)){case "amd64","x86_64"->"win-x64";case "aarch64","arm64"->"win-arm64";default->throw new UnsupportedOperationException("Нужна 64-битная Java для Windows");};
    }
    public static Path install(Path directory)throws Exception {
        String arch=architecture();JsonObject files;
        try(InputStream stream=NativeBinaries.class.getResourceAsStream("/hwhelper-native/catalog.json")){
            if(stream==null)throw new IOException("В сборке отсутствует каталог нативных библиотек");files=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject(arch);
        }
        String fingerprint=files.get("tdjson.dll").getAsString().substring(0,12);
        Path base=directory.toAbsolutePath().normalize().resolve("runtime/tdlib-1.8.69/"+arch+"/"+fingerprint);Files.createDirectories(base);
        for(var entry:files.entrySet()){
            String name=entry.getKey();if(!name.matches("[A-Za-z0-9_.-]+\\.dll"))throw new IOException("Некорректное имя библиотеки");
            Path target=base.resolve(name);String expected=entry.getValue().getAsString();
            if(Files.isRegularFile(target)&&hash(target).equals(expected))continue;
            Path temporary=Files.createTempFile(base,"extract-",".tmp");
            try(InputStream stream=NativeBinaries.class.getResourceAsStream("/hwhelper-native/"+arch+"/"+name)){
                if(stream==null)throw new IOException("В JAR отсутствует "+name);Files.copy(stream,temporary,StandardCopyOption.REPLACE_EXISTING);
                if(!hash(temporary).equals(expected))throw new IOException("Повреждена библиотека "+name);
                Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(temporary);}
        }return base;
    }
    public static synchronized NativeLibrary loadTelegram(Path directory)throws Exception {
        Path base=install(directory);String arch=architecture(),suffix=arch.equals("win-x64")?"x64":"arm64";
        var kernel=NativeLibrary.getInstance("kernel32",WindowsNative.OPTIONS);
        for(String name:List.of("z.dll","libcrypto-3-"+suffix+".dll","libssl-3-"+suffix+".dll","tdjson.dll")){
            Pointer handle=kernel.getFunction("LoadLibraryExW").invokePointer(new Object[]{new WString(base.resolve(name).toString()),null,0x1100});
            if(handle==null){int error=kernel.getFunction("GetLastError").invokeInt(new Object[]{});throw new IOException("Не удалось загрузить "+name+" (Windows "+error+"). Проверьте разрядность Java и системные библиотеки Visual C++.");}
            HANDLES.add(handle);
        }
        return NativeLibrary.getInstance(base.resolve("tdjson.dll").toString(),Map.of(Library.OPTION_STRING_ENCODING,"UTF-8"));
    }
    private static String hash(Path path)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(path)){byte[] buffer=new byte[65536];for(int count;(count=input.read(buffer))!=-1;)digest.update(buffer,0,count);}return HexFormat.of().formatHex(digest.digest());
    }
}
