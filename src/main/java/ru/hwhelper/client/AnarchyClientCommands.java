package ru.hwhelper.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.function.*;
import java.util.regex.*;

final class AnarchyClientCommands {
    private static final Pattern NUMERIC=Pattern.compile("^(?i:an)([+-]?[0-9][^\\s]*)(?:\\s.*)?$");
    static final String USAGE="HWhelper: /an7 — перейти на СолоЛайт #7; доступны /an1–/an36. Escape отменяет переход.";
    static void install(AnarchySwitcher switcher){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->register(dispatcher,
                (source,n)->{boolean accepted=switcher.join(n);if(!accepted)source.sendError(Component.literal(switcher.notice()));return accepted?1:0;},
                source->source.sendFeedback(Component.literal(USAGE))));
        // Unknown numeric roots would otherwise fall back to the server's dispatcher.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command->{
            if(!invalidNumeric(command))return true;
            var client=Minecraft.getInstance();if(client.player!=null)client.player.displayClientMessage(Component.literal("HWhelper: неизвестная анархия. Используйте /an1–/an36 без пробела."),false);
            return false;
        });
    }
    static <S> void register(CommandDispatcher<S> dispatcher,ToIntBiFunction<S,Integer> join,Consumer<S> help){
        dispatcher.register(LiteralArgumentBuilder.<S>literal("an").executes(context->{help.accept(context.getSource());return 1;})
                .then(RequiredArgumentBuilder.<S,String>argument("usage",StringArgumentType.greedyString()).executes(context->{help.accept(context.getSource());return 0;})));
        for(int number=1;number<=36;number++){
            int target=number;
            dispatcher.register(LiteralArgumentBuilder.<S>literal(AnarchyNavigation.command(target)).executes(context->join.applyAsInt(context.getSource(),target))
                    .then(RequiredArgumentBuilder.<S,String>argument("extra",StringArgumentType.greedyString()).executes(context->{help.accept(context.getSource());return 0;})));
        }
    }
    static boolean invalidNumeric(String command){
        String text=command.strip();Matcher match=NUMERIC.matcher(text);if(!match.matches())return false;
        try{int number=Integer.parseInt(match.group(1));return AnarchyNavigation.group(number)<0||!text.split("\\s+",2)[0].equals(AnarchyNavigation.command(number));}
        catch(NumberFormatException e){return true;}
    }
}
