package ru.hwhelper.client;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** A tiny user resource pack overrides only this mod's key sound; vanilla sounds are untouched. */
final class CustomKeySound {
    static String status="Добавьте свой OGG Vorbis или выберите стандартный звук";
    static boolean busy;
    private static final String PACK="HWhelper-KeySound",PACK_ID="file/"+PACK;
    static Path directory(){return FabricLoader.getInstance().getConfigDir().resolve("hwhelper/sounds");}
    static List<Path> files(){
        try{Files.createDirectories(directory());try(var stream=Files.list(directory())){return stream.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")).sorted().toList();}}
        catch(IOException e){status="Не удалось прочитать папку звуков";return List.of();}
    }
    static void choose(HwConfig c,Runnable refresh){
        if(busy)return;
        try(MemoryStack stack=MemoryStack.stackPush()){
            PointerBuffer filters=stack.mallocPointer(1);filters.put(stack.UTF8("*.ogg")).flip();
            String selected=TinyFileDialogs.tinyfd_openFileDialog("HWhelper — звук уника",directory().toAbsolutePath()+"/",filters,"OGG Vorbis",false);
            if(selected==null)return;
            Path source=Path.of(selected);validate(source);Files.createDirectories(directory());
            String name=source.getFileName().toString().replaceAll("[^\\p{L}\\p{N}._ -]","_");
            Path target=directory().resolve(name).normalize();
            if(!target.toAbsolutePath().getParent().equals(directory().toAbsolutePath().normalize()))throw new IOException("Некорректное имя файла");
            if(!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize()))Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);
            select(c,name,refresh);
        }catch(Exception e){status=e.getMessage()==null?"Ошибка импорта звука":e.getMessage();refresh.run();}
    }
    static void validate(Path path)throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>20*1024*1024)throw new IOException("Нужен файл OGG Vorbis размером до 20 МБ");
        byte[] head;try(var input=Files.newInputStream(path)){head=input.readNBytes(128);}
        String marker=new String(head,java.nio.charset.StandardCharsets.ISO_8859_1);
        if(!marker.startsWith("OggS")||!marker.contains("vorbis"))throw new IOException("Нужен OGG Vorbis; MP3, WAV и Opus сначала конвертируйте в OGG Vorbis");
    }
    static void select(HwConfig c,String name,Runnable refresh){
        if(busy)return;
        Minecraft client=Minecraft.getInstance();
        try{
            var repository=client.getResourcePackRepository();
            List<String> selected=new ArrayList<>(repository.getSelectedIds());selected.remove(PACK_ID);
            if(!name.isBlank()){
                Path sound=directory().resolve(name).normalize();
                if(!sound.getParent().equals(directory().normalize()))throw new IOException("Некорректный путь звука");validate(sound);
                Path pack=client.getResourcePackDirectory().resolve(PACK);
                Path assets=pack.resolve("assets/hwhelper/sounds");Files.createDirectories(assets);
                Files.copy(sound,assets.resolve("unique_key.ogg"),StandardCopyOption.REPLACE_EXISTING);
                var version=SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
                JsonObject root=new JsonObject(),metadata=new JsonObject();metadata.addProperty("description","HWhelper — пользовательский звук уника");metadata.addProperty("pack_format",version.major());
                var format=new com.google.gson.JsonArray();format.add(version.major());format.add(version.minor());metadata.add("min_format",format);metadata.add("max_format",format.deepCopy());root.add("pack",metadata);
                Files.writeString(pack.resolve("pack.mcmeta"),root.toString());
                repository.reload();
                if(!repository.isAvailable(PACK_ID))throw new IOException("Minecraft не распознал пакет звука");selected.add(PACK_ID);
            }
            repository.setSelected(selected);client.options.updateResourcePacks(repository);client.options.save();
            String previous=c.keySoundFile;c.keySoundFile=name;c.save();busy=true;status="Применяю звук…";refresh.run();
            client.reloadResourcePacks().whenComplete((ignored,error)->client.execute(()->{
                busy=false;
                if(error==null)status=name.isBlank()?"Стандартный звук включён":"Выбран: "+name;
                else{status="Не удалось загрузить звук. Выберите стандартный.";c.keySoundFile=previous;c.save();}
                refresh.run();
            }));
        }catch(Exception e){status=e.getMessage()==null?"Ошибка выбора звука":e.getMessage();refresh.run();}
    }
    static void preview(HwConfig c){Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("hwhelper","unique_key")),1,c.keySoundVolume));}
}
