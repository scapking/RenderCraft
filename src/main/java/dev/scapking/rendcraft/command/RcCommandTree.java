package dev.scapking.rendcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RcCommandTree {

    private static final Logger LOGGER = LoggerFactory.getLogger(RcCommandTree.class);
    private static final SimpleCommandExceptionType ERROR_UNKNOWN_WINDOW =
            new SimpleCommandExceptionType(Component.translatable("command.rc.unknown_window", "未知窗口"));
    private static final SimpleCommandExceptionType ERROR_INVALID_SUBCOMMAND =
            new SimpleCommandExceptionType(Component.translatable("command.rc.invalid_subcommand", "无效子命令"));

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> root = Literals.literal("rc")
                .then(Literals.literal("window").then(windowSubtree()))
                .then(Literals.literal("layout").then(layoutSubtree()))
                .then(Literals.literal("share").then(shareSubtree()))
                .then(Literals.literal("input").then(inputSubtree()))
                .then(Literals.literal("protocol").then(protocolSubtree()))
                .then(Literals.literal("setting").then(settingSubtree()))
                .then(Literals.literal("help").executes(RcCommandTree::help))
                .then(Literals.literal("version").executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Component.literal("RenderCraft 0.1.0"), true);
                    return 1;
                }));

        dispatcher.register(root);
    }

    private static ArgumentBuilder<CommandSource, ?> windowSubtree() {
        return Literals.literal("window")
                .then(Literals.literal("list").executes(ctx -> listWindows(ctx)))
                .then(Literals.literal("capture").executes(ctx -> captureWindow(ctx, Arg.str(ctx, "handle"))))
                .then(Literals.literal("show").then(Literals.literal("*").executes(ctx -> showAllWindows(ctx)))
                        .then(Literals.literal("hidden").executes(ctx -> showHiddenWindows(ctx))))
                        .then(Literals.literal("hide").then(Literals.literal("*").executes(ctx -> hideAllWindows(ctx)))
                                .then(Literals.literal("visible").executes(ctx -> hideVisibleWindows(ctx))))
                                .then(Literals.literal("give").then(Literals.literal("*").executes(ctx -> giveWindow(ctx, Arg.str(ctx, "handle"))))
                                        .then(Literals.literal("grab").then(Literals.literal("*").executes(ctx -> grabWindow(ctx, Arg.str(ctx, "handle"))))
                                                .then(Literals.literal("resize").then(Literals.literal("*")
                                                        .executes(ctx -> resizeWindow(ctx, Arg.str(ctx, "handle"), Arg.integer(ctx, "w"), Arg.integer(ctx, "h")))
                                                        .then(Literals.literal("pos").then(Literals.literal("*")
                                                                .executes(ctx -> moveWindow(ctx, Arg.str(ctx, "handle"), Arg.decimal(ctx, "x"), Arg.decimal(ctx, "y"), Arg.decimal(ctx, "z")))
                                                                .then(Literals.literal("rotate").then(Literals.literal("*")
                                                                        .executes(ctx -> rotateWindow(ctx, Arg.str(ctx, "handle"), Arg.decimal(ctx, "angle")))
                                                                        .then(Literals.literal("pin").then(Literals.literal("*")
                                                                                .executes(ctx -> pinWindow(ctx, Arg.str(ctx, "handle")))
                                                                                .then(Literals.literal("unpin").then(Literals.literal("*")
                                                                                        .executes(ctx -> unpinWindow(ctx, Arg.str(ctx, "handle")))
                                                                                        .then(Literals.literal("close").then(Literals.literal("*")
                                                                                                .executes(ctx -> closeWindow(ctx, Arg.str(ctx, "handle")))
                                                                                                .then(Literals.literal("x11").then(Literals.literal("list")
                                                                                                        .executes(ctx -> listX11Windows(ctx))
                                                                                                        .then(Literals.literal("share").then(Literals.literal("*")
                                                                                                                .executes(ctx -> shareX11Window(ctx, Arg.integer(ctx, "index")))))))))))))))))))))))))
                ;
    }

    private static int listWindows(CommandContext<CommandSource> ctx) { return 0; }
    private static int captureWindow(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int showAllWindows(CommandContext<CommandSource> ctx) { return 0; }
    private static int showHiddenWindows(CommandContext<CommandSource> ctx) { return 0; }
    private static int hideAllWindows(CommandContext<CommandSource> ctx) { return 0; }
    private static int hideVisibleWindows(CommandContext<CommandSource> ctx) { return 0; }
    private static int giveWindow(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int grabWindow(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int resizeWindow(CommandContext<CommandSource> ctx, String handle, int w, int h) { return 0; }
    private static int moveWindow(CommandContext<CommandSource> ctx, String handle, double x, double y, double z) { return 0; }
    private static int rotateWindow(CommandContext<CommandSource> ctx, String handle, double angle) { return 0; }
    private static int pinWindow(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int unpinWindow(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int closeWindow(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int listX11Windows(CommandContext<CommandSource> ctx) { return 0; }
    private static int shareX11Window(CommandContext<CommandSource> ctx, int index) { return 0; }

    private static ArgumentBuilder<CommandSource, ?> layoutSubtree() {
        return Literals.literal("layout")
                .then(Literals.literal("init").executes(ctx -> layoutInit(ctx)))
                        .then(Literals.literal("cube").executes(ctx -> layoutCube(ctx))
                                .then(Literals.literal("sphere").executes(ctx -> layoutSphere(ctx))
                                        .then(Literals.literal("on").executes(ctx -> layoutOn(ctx))
                                                .then(Literals.literal("off").executes(ctx -> layoutOff(ctx))
                                                        .then(Literals.literal("toggle").executes(ctx -> layoutToggle(ctx))
                                                                .then(Literals.literal("status").executes(ctx -> layoutStatus(ctx))
                                                                        .then(Literals.literal("list").executes(ctx -> layoutList(ctx))
                                                                                .then(Literals.literal("add").then(Literals.literal("*")
                                                                                        .executes(ctx -> layoutAdd(ctx, Arg.str(ctx, "handle")))
                                                                                        .then(Literals.literal("remove").then(Literals.literal("*")
                                                                                                .executes(ctx -> layoutRemove(ctx, Arg.str(ctx, "handle"))))))))))))))
                .then(Literals.literal("template").then(templateSubtree()));
    }

    private static int layoutInit(CommandContext<CommandSource> ctx) { return 0; }
    private static int layoutCube(CommandContext<CommandSource> ctx) { return 0; }
    private static int layoutSphere(CommandContext<CommandSource> ctx) { return 0; }
    private static int layoutOn(CommandContext<CommandSource> ctx) { return 0; }
    private static int layoutOff(CommandContext<CommandSource> ctx) { return 0; }
    private static int layoutToggle(CommandContext<CommandSource> ctx) { return 0; }
    private static int layoutStatus(CommandContext<CommandSource> ctx) { return 0; }
    private static int layoutList(CommandContext<CommandSource> ctx) { return 0; }
    private static int layoutAdd(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int layoutRemove(CommandContext<CommandSource> ctx, String handle) { return 0; }

    private static ArgumentBuilder<CommandSource, ?> templateSubtree() {
        return Literals.literal("template")
                .then(Literals.literal("save").then(Literals.literal("*")
                        .executes(ctx -> templateSave(ctx, Arg.str(ctx, "name")))
                        .then(Literals.literal("apply").then(Literals.literal("*")
                                .executes(ctx -> templateApply(ctx, Arg.str(ctx, "name")))
                                .then(Literals.literal("list").executes(ctx -> templateList(ctx))
                                        .then(Literals.literal("remove").then(Literals.literal("*")
                                                .executes(ctx -> templateRemove(ctx, Arg.str(ctx, "name")))))))
                                                .then(Literals.literal("savep").then(Literals.literal("*")
                                                        .executes(ctx -> templateSavePermanent(ctx, Arg.str(ctx, "name")))
                                                        .then(Literals.literal("applyp").then(Literals.literal("*")
                                                                .executes(ctx -> templateApplyPermanent(ctx, Arg.str(ctx, "name")))
                                                                .then(Literals.literal("removep").then(Literals.literal("*")
                                                                        .executes(ctx -> templateRemovePermanent(ctx, Arg.str(ctx, "name"))))))))))))
                ;
    }

    private static int templateSave(CommandContext<CommandSource> ctx, String name) { return 0; }
    private static int templateApply(CommandContext<CommandSource> ctx, String name) { return 0; }
    private static int templateList(CommandContext<CommandSource> ctx) { return 0; }
    private static int templateRemove(CommandContext<CommandSource> ctx, String name) { return 0; }
    private static int templateSavePermanent(CommandContext<CommandSource> ctx, String name) { return 0; }
    private static int templateApplyPermanent(CommandContext<CommandSource> ctx, String name) { return 0; }
    private static int templateRemovePermanent(CommandContext<CommandSource> ctx, String name) { return 0; }

    private static ArgumentBuilder<CommandSource, ?> shareSubtree() {
        return Literals.literal("share")
                .then(Literals.literal("start").then(Literals.literal("*")
                        .executes(ctx -> shareStart(ctx, Arg.str(ctx, "handle")))
                        .then(Literals.literal("stop").then(Literals.literal("*")
                                .executes(ctx -> shareStop(ctx, Arg.str(ctx, "handle")))
                                .then(Literals.literal("quality").then(Literals.literal("*")
                                        .executes(ctx -> shareQuality(ctx, Arg.str(ctx, "handle"),
                                                Arg.decimal(ctx, "scale"), Arg.decimal(ctx, "quality"), Arg.integer(ctx, "fps"))))))
                                        .then(Literals.literal("preset").then(Literals.literal("*")
                                                .executes(ctx -> sharePreset(ctx, Arg.str(ctx, "handle"), Arg.str(ctx, "preset"))))
                                                .then(Literals.literal("config").then(Literals.literal("*")
                                                        .executes(ctx -> shareConfig(ctx, Arg.str(ctx, "handle"),
                                                                Arg.str(ctx, "param"), Arg.decimal(ctx, "value"))))
                                                        .then(Literals.literal("reset").then(Literals.literal("*")
                                                                .executes(ctx -> shareReset(ctx, Arg.str(ctx, "handle"))))
                                                                .then(Literals.literal("info").then(Literals.literal("*")
                                                                        .executes(ctx -> shareInfo(ctx, Arg.str(ctx, "handle"))))
                                                                        .then(Literals.literal("resolution").then(Literals.literal("*")
                                                                                .executes(ctx -> shareResolution(ctx, Arg.str(ctx, "handle"),
                                                                                        Arg.integer(ctx, "w"), Arg.integer(ctx, "h")))
                                                                                        .then(Literals.literal("stats").then(Literals.literal("*")
                                                                                                .executes(ctx -> shareStats(ctx, Arg.str(ctx, "handle"))))))))))))))
                ;
    }

    private static int shareStart(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int shareStop(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int shareQuality(CommandContext<CommandSource> ctx, String handle, double scale, double quality, int fps) { return 0; }
    private static int sharePreset(CommandContext<CommandSource> ctx, String handle, String preset) { return 0; }
    private static int shareConfig(CommandContext<CommandSource> ctx, String handle, String param, double value) { return 0; }
    private static int shareReset(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int shareInfo(CommandContext<CommandSource> ctx, String handle) { return 0; }
    private static int shareResolution(CommandContext<CommandSource> ctx, String handle, int w, int h) { return 0; }
    private static int shareStats(CommandContext<CommandSource> ctx, String handle) { return 0; }

    private static ArgumentBuilder<CommandSource, ?> inputSubtree() {
        return Literals.literal("input")
                .then(Literals.literal("capture-mode").executes(ctx -> inputCaptureMode(ctx))
                        .then(Literals.literal("toggle-grab").executes(ctx -> inputToggleGrab(ctx))
                                .then(Literals.literal("release").executes(ctx -> inputRelease(ctx))
                                        .then(Literals.literal("status").executes(ctx -> inputStatus(ctx)))));
    }

    private static int inputCaptureMode(CommandContext<CommandSource> ctx) { return 0; }
    private static int inputToggleGrab(CommandContext<CommandSource> ctx) { return 0; }
    private static int inputRelease(CommandContext<CommandSource> ctx) { return 0; }
    private static int inputStatus(CommandContext<CommandSource> ctx) { return 0; }

    private static ArgumentBuilder<CommandSource, ?> protocolSubtree() {
        return Literals.literal("protocol")
                .then(Literals.literal("list").executes(ctx -> protocolList(ctx))
                        .then(Literals.literal("current").executes(ctx -> protocolCurrent(ctx))
                                .then(Literals.literal("switch").then(Literals.literal("*")
                                        .executes(ctx -> protocolSwitch(ctx, Arg.str(ctx, "protocol")))
                                        .then(Literals.literal("windows").then(Literals.literal("*")
                                                .executes(ctx -> protocolWindows(ctx, Arg.str(ctx, "protocol"))))));
    }

    private static int protocolList(CommandContext<CommandSource> ctx) { return 0; }
    private static int protocolCurrent(CommandContext<CommandSource> ctx) { return 0; }
    private static int protocolSwitch(CommandContext<CommandSource> ctx, String protocol) { return 0; }
    private static int protocolWindows(CommandContext<CommandSource> ctx, String protocol) { return 0; }

    private static ArgumentBuilder<CommandSource, ?> settingSubtree() {
        return Literals.literal("setting")
                .then(Literals.literal("list").executes(ctx -> settingList(ctx))
                        .then(Literals.literal("set").then(Literals.literal("*")
                                .executes(ctx -> settingSet(ctx, Arg.str(ctx, "key"), Arg.str(ctx, "value")))));
    }

    private static int settingList(CommandContext<CommandSource> ctx) { return 0; }
    private static int settingSet(CommandContext<CommandSource> ctx, String key, String value) { return 0; }

    private static int help(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Component.literal(
                "RenderCraft 命令列表:\n" +
                        "  /rc window list\n" +
                        "  /rc window capture <handle>\n" +
                        "  /rc window show/hide\n" +
                        "  /rc window give/grab/resize/pos/rotate/pin/unpin/close\n" +
                        "  /rc layout init/cube/sphere/on/off/toggle/status/list\n" +
                        "  /rc layout template save/apply/list/remove [savep|applyp|removep]\n" +
                        "  /rc share start/stop/quality/preset/config/reset/info/resolution/stats\n" +
                        "  /rc input capture-mode/toggle-grab/release/status\n" +
                        "  /rc protocol list/current/switch/windows\n" +
                        "  /rc setting list/set\n" +
                        "  /rc help\n" +
                        "  /rc version"
        ), true);
        return 1;
    }

    private static final class Literals {
        static <S> LiteralArgumentBuilder<S> literal(String name) {
            return LiteralArgumentBuilder.literal(name);
        }
    }

    private static final class Arg {
        static String str(CommandContext<CommandSource> ctx, String name) {
            return ctx.getArgument(name, String.class);
        }

        static int integer(CommandContext<CommandSource> ctx, String name) {
            return ctx.getArgument(name, int.class);
        }

        static double decimal(CommandContext<CommandSource> ctx, String name) {
            return ctx.getArgument(name, double.class);
        }
    }
}
