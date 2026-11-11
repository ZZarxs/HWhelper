package ru.hwhelper.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.function.*;
import java.util.regex.*;

final class AnarchyClientCommands {
    static final String USAGE="HWhelper / HubSwap: /an 7, /lt 7 или /an7. F6 — диапазоны и алиасы; Escape отменяет переход.";
    record Input(boolean owned,Integer target,String error){}
    static void install(AnarchySwitcher switcher,AnarchyOptions options){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->register(dispatcher,options,
                (source,n)->{boolean ok=switcher.join(n);if(!ok)source.sendError(Component.literal(switcher.notice()));return ok?1:0;},
                source->source.sendFeedback(Component.literal(USAGE))));
        // One local handler also covers newly edited aliases before the next command-tree refresh.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command->{
            Input input=parse(command,options);if(!input.owned())return true;
            var player=Minecraft.getInstance().player;
            if(input.target()!=null){if(!switcher.join(input.target())&&player!=null)player.displayClientMessage(Component.literal(switcher.notice()),false);}
            else if(player!=null)player.displayClientMessage(Component.literal(input.error().isBlank()?USAGE:input.error()),false);
            return false;
        });
    }
    static <S> void register(CommandDispatcher<S> dispatcher,ToIntBiFunction<S,Integer> join,Consumer<S> help){register(dispatcher,new AnarchyOptions(),join,help);}
    static <S> void register(CommandDispatcher<S> dispatcher,AnarchyOptions options,ToIntBiFunction<S,Integer> join,Consumer<S> help){
        for(String alias:options.aliases){
            dispatcher.register(LiteralArgumentBuilder.<S>literal(alias).executes(c->{help.accept(c.getSource());return 1;})
                    .then(RequiredArgumentBuilder.<S,Integer>argument("number",IntegerArgumentType.integer(1,HubSwapRanges.LIMIT))
                            .suggests((c,b)->{for(int n=1;n<=options.ranges.total;n++){String text=Integer.toString(n);if(text.startsWith(b.getRemaining()))b.suggest(text);}return b.buildFuture();})
                            .executes(c->{int n=IntegerArgumentType.getInteger(c,"number");if(options.ranges.group(n)<0||!options.aliases.contains(alias)){help.accept(c.getSource());return 0;}return join.applyAsInt(c.getSource(),n);} )));
        }
        for(int n=1;n<=options.ranges.total;n++){
            int target=n;dispatcher.register(LiteralArgumentBuilder.<S>literal("an"+target).executes(c->{if(options.ranges.group(target)<0){help.accept(c.getSource());return 0;}return join.applyAsInt(c.getSource(),target);})
                    .then(RequiredArgumentBuilder.<S,String>argument("extra",StringArgumentType.greedyString()).executes(c->{help.accept(c.getSource());return 0;})));
        }
    }
    static Input parse(String command,AnarchyOptions options){
        String[] parts=command.strip().split("\\s+",3);String root=parts[0];String number=null;
        if(options.aliases.contains(root)){
            if(parts.length==1)return new Input(true,null,"");
            if(parts.length!=2)return new Input(true,null,USAGE);number=parts[1];
        }else{
            Matcher match=Pattern.compile("^(?i:an)([+-]?[0-9].*)$").matcher(root);
            if(!match.matches())return new Input(false,null,"");
            if(parts.length!=1||!root.startsWith("an"))return new Input(true,null,USAGE);number=match.group(1);
        }
        try{int n=Integer.parseInt(number);if(!number.equals(Integer.toString(n))||options.ranges.group(n)<0)throw new NumberFormatException();return new Input(true,n,"");}
        catch(NumberFormatException e){return new Input(true,null,"HWhelper: допустимы анархии 1–"+options.ranges.total+". Пример: /an 7");}
    }
    static boolean invalidNumeric(String command){Input input=parse(command,new AnarchyOptions());return input.owned()&&input.target()==null&&!input.error().isBlank();}
}
