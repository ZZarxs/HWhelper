package ru.hwhelper.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** One-time import of the original mod's two plain-text preferences, never its code or session data. */
final class HubSwapLegacyImport {
    static HubSwapRanges parseRanges(String text){
        Map<String,String> values=new HashMap<>();
        for(String line:text.split("\\R")){
            String value=line.strip();if(value.isBlank()||value.startsWith("#"))continue;
            String[] parts=value.split("=",2);if(parts.length!=2)throw new IllegalArgumentException("Неверная строка диапазона");
            String key=parts[0].strip();if(!Set.of("total","solo","duo","trio","clans").contains(key))continue;
            if(values.putIfAbsent(key,parts[1].strip())!=null)throw new IllegalArgumentException("Повторный диапазон: "+key);
        }
        if(!values.keySet().containsAll(Set.of("total","solo","duo","trio","clans")))throw new IllegalArgumentException("Не все диапазоны указаны");
        return HubSwapRanges.parse(Integer.parseInt(values.get("total")),Arrays.stream(HubSwapRanges.KEYS).map(values::get).toList());
    }
    static void importOnce(HwConfig config){
        AnarchyOptions options=config.anarchy;if(options.hubSwapImported)return;
        Path directory=FabricLoader.getInstance().getConfigDir();
        try{Path path=directory.resolve("krshubswap-ranges.txt");if(Files.isRegularFile(path)&&Files.size(path)<=65536)options.ranges=parseRanges(Files.readString(path));}
        catch(Exception e){HWhelperClient.LOGGER.warn("Could not import krsHubSwap ranges: {}",e.getClass().getSimpleName());}
        try{Path path=directory.resolve("krshubswap-aliases.txt");if(Files.isRegularFile(path)&&Files.size(path)<=65536){var names=Files.readAllLines(path).stream().map(String::strip).filter(s->!s.isBlank()).toList();if(!names.isEmpty())options.aliases=HubSwapRanges.aliases(names);}}
        catch(Exception e){HWhelperClient.LOGGER.warn("Could not import krsHubSwap aliases: {}",e.getClass().getSimpleName());}
        options.hubSwapImported=true;options.sanitize();config.save();
    }
}
