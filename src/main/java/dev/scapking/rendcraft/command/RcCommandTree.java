package dev.scapking.rendcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.scapking.rendcraft.RenderCraftRuntime;
import dev.scapking.rendcraft.protocol.FrameSnapshot;
import dev.scapking.rendcraft.protocol.ProtocolException;
import dev.scapking.rendcraft.protocol.ProtocolType;
import dev.scapking.rendcraft.protocol.WindowHandle;
import dev.scapking.rendcraft.protocol.WindowMetadata;
import dev.scapking.rendcraft.protocol.X11Adapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RenderCraft command tree (/{@code /rc ...}).
 *
 * <p>The previous hand-rolled .then() chain looked concise but used
 * {@code literal("*")} as a wildcard (which Brigadier cannot parse as a
 * captured argument) and accidentally nested every subcommand under the
 * first sibling. This rewrite uses {@link StringArgumentType#word()} for
 * real argument capture, builds the tree bottom-up so each subtree is a
 * self-contained {@link ArgumentBuilder}, and only the wire-up at the
 * {@code root} calls {@code .then()}.
 *
 * <p>All handlers are stubs returning 0 today. Each will be implemented
 * alongside the manager / encoder / listener that backs the verb.
 */
public final class RcCommandTree {

    private static final Logger LOGGER = LoggerFactory.getLogger(RcCommandTree.class);
    private static final SimpleCommandExceptionType ERROR_UNKNOWN_WINDOW =
            new SimpleCommandExceptionType(Component.literal("Unknown window handle"));
    private static final SimpleCommandExceptionType ERROR_INVALID_SUBCOMMAND =
            new SimpleCommandExceptionType(Component.literal("Invalid subcommand"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("rc")
                .then(windowSubtree())
                .then(layoutSubtree())
                .then(shareSubtree())
                .then(inputSubtree())
                .then(protocolSubtree())
                .then(settingSubtree())
                .then(literal("help").executes(RcCommandTree::help))
                .then(literal("version").executes(ctx -> {
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("RenderCraft 0.1.0"), true);
                    return 1;
                })));
    }

    // ------------------------------------------------------------------
    // Subtree builders — each is independently balanced.
    // ------------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> windowSubtree() {
        return literal("window")
                .then(literal("list").executes(RcCommandTree::listWindows))
                .then(literal("capture").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> captureWindow(ctx, str(ctx, "handle")))))
                .then(literal("show")
                        .then(literal("*").executes(RcCommandTree::showAllWindows))
                        .then(literal("hidden").executes(RcCommandTree::showHiddenWindows)))
                .then(literal("hide")
                        .then(literal("*").executes(RcCommandTree::hideAllWindows))
                        .then(literal("visible").executes(RcCommandTree::hideVisibleWindows)))
                .then(literal("give").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> giveWindow(ctx, str(ctx, "handle")))))
                .then(literal("grab").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> grabWindow(ctx, str(ctx, "handle")))))
                .then(literal("resize").then(arg("handle", StringArgumentType.word())
                        .then(arg("w", IntegerArgumentType.integer(1))
                                .then(arg("h", IntegerArgumentType.integer(1))
                                        .executes(ctx -> resizeWindow(ctx,
                                                str(ctx, "handle"),
                                                intArg(ctx, "w"),
                                                intArg(ctx, "h")))))))
                .then(literal("pos").then(arg("handle", StringArgumentType.word())
                        .then(arg("x", DoubleArgumentType.doubleArg())
                                .then(arg("y", DoubleArgumentType.doubleArg())
                                        .then(arg("z", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> moveWindow(ctx,
                                                        str(ctx, "handle"),
                                                        dbl(ctx, "x"),
                                                        dbl(ctx, "y"),
                                                        dbl(ctx, "z"))))))))
                .then(literal("rotate").then(arg("handle", StringArgumentType.word())
                        .then(arg("angle", DoubleArgumentType.doubleArg())
                                .executes(ctx -> rotateWindow(ctx,
                                        str(ctx, "handle"),
                                        dbl(ctx, "angle"))))))
                .then(literal("pin").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> pinWindow(ctx, str(ctx, "handle")))))
                .then(literal("unpin").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> unpinWindow(ctx, str(ctx, "handle")))))
                .then(literal("close").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> closeWindow(ctx, str(ctx, "handle")))))
                .then(literal("x11")
                        .then(literal("list").executes(RcCommandTree::listX11Windows))
                        .then(literal("share").then(arg("index", IntegerArgumentType.integer(0))
                                .executes(ctx -> shareX11Window(ctx, intArg(ctx, "index"))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> layoutSubtree() {
        return literal("layout")
                .then(literal("init").executes(RcCommandTree::layoutInit))
                .then(literal("cube").executes(RcCommandTree::layoutCube))
                .then(literal("sphere").executes(RcCommandTree::layoutSphere))
                .then(literal("on").executes(RcCommandTree::layoutOn))
                .then(literal("off").executes(RcCommandTree::layoutOff))
                .then(literal("toggle").executes(RcCommandTree::layoutToggle))
                .then(literal("status").executes(RcCommandTree::layoutStatus))
                .then(literal("list").executes(RcCommandTree::layoutList))
                .then(literal("add").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> layoutAdd(ctx, str(ctx, "handle")))))
                .then(literal("remove").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> layoutRemove(ctx, str(ctx, "handle")))))
                .then(literal("template").then(templateSubtree()));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> templateSubtree() {
        return literal("template")
                .then(literal("save").then(arg("name", StringArgumentType.word())
                        .executes(ctx -> templateSave(ctx, str(ctx, "name")))))
                .then(literal("apply").then(arg("name", StringArgumentType.word())
                        .executes(ctx -> templateApply(ctx, str(ctx, "name")))))
                .then(literal("list").executes(RcCommandTree::templateList))
                .then(literal("remove").then(arg("name", StringArgumentType.word())
                        .executes(ctx -> templateRemove(ctx, str(ctx, "name")))))
                .then(literal("savep").then(arg("name", StringArgumentType.word())
                        .executes(ctx -> templateSavePermanent(ctx, str(ctx, "name")))))
                .then(literal("applyp").then(arg("name", StringArgumentType.word())
                        .executes(ctx -> templateApplyPermanent(ctx, str(ctx, "name")))))
                .then(literal("removep").then(arg("name", StringArgumentType.word())
                        .executes(ctx -> templateRemovePermanent(ctx, str(ctx, "name")))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> shareSubtree() {
        return literal("share")
                .then(literal("start").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> shareStart(ctx, str(ctx, "handle")))))
                .then(literal("stop").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> shareStop(ctx, str(ctx, "handle")))))
                .then(literal("quality").then(arg("handle", StringArgumentType.word())
                        .then(arg("scale", DoubleArgumentType.doubleArg(0.0))
                                .then(arg("quality", DoubleArgumentType.doubleArg(0.0, 1.0))
                                        .then(arg("fps", IntegerArgumentType.integer(1))
                                                .executes(ctx -> shareQuality(ctx,
                                                        str(ctx, "handle"),
                                                        dbl(ctx, "scale"),
                                                        dbl(ctx, "quality"),
                                                        intArg(ctx, "fps"))))))))
                .then(literal("preset").then(arg("handle", StringArgumentType.word())
                        .then(arg("preset", StringArgumentType.word())
                                .executes(ctx -> sharePreset(ctx,
                                        str(ctx, "handle"),
                                        str(ctx, "preset"))))))
                .then(literal("config").then(arg("handle", StringArgumentType.word())
                        .then(arg("param", StringArgumentType.word())
                                .then(arg("value", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> shareConfig(ctx,
                                                str(ctx, "handle"),
                                                str(ctx, "param"),
                                                dbl(ctx, "value")))))))
                .then(literal("reset").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> shareReset(ctx, str(ctx, "handle")))))
                .then(literal("info").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> shareInfo(ctx, str(ctx, "handle")))))
                .then(literal("resolution").then(arg("handle", StringArgumentType.word())
                        .then(arg("w", IntegerArgumentType.integer(1))
                                .then(arg("h", IntegerArgumentType.integer(1))
                                        .executes(ctx -> shareResolution(ctx,
                                                str(ctx, "handle"),
                                                intArg(ctx, "w"),
                                                intArg(ctx, "h")))))))
                .then(literal("stats").then(arg("handle", StringArgumentType.word())
                        .executes(ctx -> shareStats(ctx, str(ctx, "handle")))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> inputSubtree() {
        return literal("input")
                .then(literal("capture-mode").executes(RcCommandTree::inputCaptureMode))
                .then(literal("toggle-grab").executes(RcCommandTree::inputToggleGrab))
                .then(literal("release").executes(RcCommandTree::inputRelease))
                .then(literal("status").executes(RcCommandTree::inputStatus));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> protocolSubtree() {
        return literal("protocol")
                .then(literal("list").executes(RcCommandTree::protocolList))
                .then(literal("current").executes(RcCommandTree::protocolCurrent))
                .then(literal("switch").then(arg("protocol", StringArgumentType.word())
                        .executes(ctx -> protocolSwitch(ctx, str(ctx, "protocol")))))
                .then(literal("windows").then(arg("protocol", StringArgumentType.word())
                        .executes(ctx -> protocolWindows(ctx, str(ctx, "protocol")))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> settingSubtree() {
        return literal("setting")
                .then(literal("list").executes(RcCommandTree::settingList))
                .then(literal("set").then(arg("key", StringArgumentType.word())
                        .then(arg("value", StringArgumentType.greedyString())
                                .executes(ctx -> settingSet(ctx,
                                        str(ctx, "key"),
                                        str(ctx, "value"))))));
    }

    // ------------------------------------------------------------------
    // Stubs — real implementations follow once the corresponding
    // manager/encoder lands.
    // ------------------------------------------------------------------

    private static int listWindows(CommandContext<CommandSourceStack> ctx) {
        try {
            RenderCraftRuntime.refreshWindows();
            WindowHandle[] handles =
                    RenderCraftRuntime.getWindowManager().listWindows();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "RenderCraft: " + handles.length + " window(s) known"), true);
            for (WindowHandle h : handles) {
                WindowMetadata m =
                        RenderCraftRuntime.getWindowManager().getMetadata(h);
                if (m != null) {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "  " + h.getId() + " :: " + m.getTitle()
                                    + " (" + m.getWidth() + "x" + m.getHeight() + ", "
                                    + (m.isVisible() ? "visible" : "hidden") + ")"), true);
                }
            }
            return handles.length;
        } catch (ProtocolException e) {
            ctx.getSource().sendFailure(Component.literal("listWindows failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int captureWindow(CommandContext<CommandSourceStack> ctx, String handle) {
        try {
            WindowHandle h =
                    new WindowHandle(handle, ProtocolType.X11);
            FrameSnapshot snap =
                    RenderCraftRuntime.getWindowManager().captureFrame(h);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Captured " + snap.getWidth() + "x" + snap.getHeight()
                            + " (" + snap.getImageData().length + " bytes)"), true);
            return 1;
        } catch (ProtocolException e) {
            ctx.getSource().sendFailure(Component.literal("captureFrame failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int showAllWindows(CommandContext<CommandSourceStack> ctx) {
        try {
            WindowHandle[] handles =
                    RenderCraftRuntime.getWindowManager().listWindows();
            for (WindowHandle h : handles) {
                RenderCraftRuntime.getWindowManager().requestShow(h);
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Requested show for " + handles.length + " window(s)"), true);
            return handles.length;
        } catch (ProtocolException e) {
            ctx.getSource().sendFailure(Component.literal("show failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int showHiddenWindows(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "show-hidden is a no-op until the protocol backend reports hidden state; "
                        + "current X11/Wayland adapters do not."), true);
        return 0;
    }

    private static int hideAllWindows(CommandContext<CommandSourceStack> ctx) {
        try {
            WindowHandle[] handles =
                    RenderCraftRuntime.getWindowManager().listWindows();
            for (WindowHandle h : handles) {
                RenderCraftRuntime.getWindowManager().requestHide(h);
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Requested hide for " + handles.length + " window(s)"), true);
            return handles.length;
        } catch (ProtocolException e) {
            ctx.getSource().sendFailure(Component.literal("hide failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int hideVisibleWindows(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "hide-visible is a no-op until the protocol backend reports visible state; "
                        + "current X11/Wayland adapters do not."), true);
        return 0;
    }

    private static int giveWindow(CommandContext<CommandSourceStack> ctx, String handle) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "give: not yet implemented (would push a captured window into the player view)."), true);
        return 0;
    }

    private static int grabWindow(CommandContext<CommandSourceStack> ctx, String handle) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "grab: not yet implemented (would attach a window to the player)."), true);
        return 0;
    }

    private static int resizeWindow(CommandContext<CommandSourceStack> ctx, String handle, int w, int h) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "resize: backend-driven; no X11/Wayland impl today (handle=" + handle
                        + ", " + w + "x" + h + ")."), true);
        return 0;
    }

    private static int moveWindow(CommandContext<CommandSourceStack> ctx, String handle, double x, double y, double z) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "move: backed by TemplateLayoutManager in a future patch "
                        + "(handle=" + handle + ", xyz=" + x + "," + y + "," + z + ")."), true);
        return 0;
    }

    private static int rotateWindow(CommandContext<CommandSourceStack> ctx, String handle, double angle) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "rotate: not yet implemented (handle=" + handle + ", angle=" + angle + ")."), true);
        return 0;
    }

    private static int pinWindow(CommandContext<CommandSourceStack> ctx, String handle) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "pin: not yet implemented (handle=" + handle + ")."), true);
        return 0;
    }

    private static int unpinWindow(CommandContext<CommandSourceStack> ctx, String handle) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "unpin: not yet implemented (handle=" + handle + ")."), true);
        return 0;
    }

    private static int closeWindow(CommandContext<CommandSourceStack> ctx, String handle) {
        try {
            WindowHandle h =
                    new WindowHandle(handle, ProtocolType.X11);
            RenderCraftRuntime.getWindowManager().requestClose(h);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Requested close for " + handle), true);
            return 1;
        } catch (RuntimeException e) {
            ctx.getSource().sendFailure(Component.literal("close failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int listX11Windows(CommandContext<CommandSourceStack> ctx) {
        try {
            java.util.List<X11Adapter.WindowInfo> wins =
                    X11Adapter.listX11Windows(null);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "X11 enumerated " + wins.size() + " top-level window(s)"), true);
            for (X11Adapter.WindowInfo w : wins) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  0x" + w.hash + " " + w.title
                                + (w.appId != null ? " [" + w.appId + "]" : "")
                                + (w.pid > 0 ? " pid=" + w.pid : "")
                                + " " + w.width + "x" + w.height
                                + (w.visible ? " visible" : " hidden")), true);
            }
            return wins.size();
        } catch (Throwable t) {
            ctx.getSource().sendFailure(Component.literal(
                    "X11 list failed: " + t.getClass().getSimpleName() + ": " + t.getMessage()));
            return 0;
        }
    }

    private static int shareX11Window(CommandContext<CommandSourceStack> ctx, int index) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "share: not yet implemented (index=" + index
                        + "); see /rc share for the planned multi-client API."), true);
        return 0;
    }

    private static int layoutInit(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int layoutCube(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int layoutSphere(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int layoutOn(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int layoutOff(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int layoutToggle(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int layoutStatus(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int layoutList(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int layoutAdd(CommandContext<CommandSourceStack> ctx, String handle) { return 0; }
    private static int layoutRemove(CommandContext<CommandSourceStack> ctx, String handle) { return 0; }

    private static int templateSave(CommandContext<CommandSourceStack> ctx, String name) { return 0; }
    private static int templateApply(CommandContext<CommandSourceStack> ctx, String name) { return 0; }
    private static int templateList(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int templateRemove(CommandContext<CommandSourceStack> ctx, String name) { return 0; }
    private static int templateSavePermanent(CommandContext<CommandSourceStack> ctx, String name) { return 0; }
    private static int templateApplyPermanent(CommandContext<CommandSourceStack> ctx, String name) { return 0; }
    private static int templateRemovePermanent(CommandContext<CommandSourceStack> ctx, String name) { return 0; }

    private static int shareStart(CommandContext<CommandSourceStack> ctx, String handle) { return 0; }
    private static int shareStop(CommandContext<CommandSourceStack> ctx, String handle) { return 0; }
    private static int shareQuality(CommandContext<CommandSourceStack> ctx, String handle, double scale, double quality, int fps) { return 0; }
    private static int sharePreset(CommandContext<CommandSourceStack> ctx, String handle, String preset) { return 0; }
    private static int shareConfig(CommandContext<CommandSourceStack> ctx, String handle, String param, double value) { return 0; }
    private static int shareReset(CommandContext<CommandSourceStack> ctx, String handle) { return 0; }
    private static int shareInfo(CommandContext<CommandSourceStack> ctx, String handle) { return 0; }
    private static int shareResolution(CommandContext<CommandSourceStack> ctx, String handle, int w, int h) { return 0; }
    private static int shareStats(CommandContext<CommandSourceStack> ctx, String handle) { return 0; }

    private static int inputCaptureMode(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int inputToggleGrab(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int inputRelease(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int inputStatus(CommandContext<CommandSourceStack> ctx) { return 0; }

    private static int protocolList(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int protocolCurrent(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int protocolSwitch(CommandContext<CommandSourceStack> ctx, String protocol) { return 0; }
    private static int protocolWindows(CommandContext<CommandSourceStack> ctx, String protocol) { return 0; }

    private static int settingList(CommandContext<CommandSourceStack> ctx) { return 0; }
    private static int settingSet(CommandContext<CommandSourceStack> ctx, String key, String value) { return 0; }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "RenderCraft command list:\n"
                        + "  /rc window list | capture <handle> | show | hide | give <handle>\n"
                        + "  /rc window grab <handle> | resize <handle> <w> <h>\n"
                        + "  /rc window pos <handle> <x> <y> <z> | rotate <handle> <angle>\n"
                        + "  /rc window pin <handle> | unpin <handle> | close <handle>\n"
                        + "  /rc window x11 list | share <index>\n"
                        + "  /rc layout init | cube | sphere | on | off | toggle | status | list\n"
                        + "  /rc layout add <handle> | remove <handle>\n"
                        + "  /rc layout template save | apply | list | remove | savep | applyp | removep <name>\n"
                        + "  /rc share start | stop | quality | preset | config | reset | info | resolution | stats <handle> ...\n"
                        + "  /rc input capture-mode | toggle-grab | release | status\n"
                        + "  /rc protocol list | current | switch | windows <protocol>\n"
                        + "  /rc setting list | set <key> <value>\n"
                        + "  /rc help | version"
        ), true);
        return 1;
    }

    // ------------------------------------------------------------------
    // Brigadier plumbing helpers.
    // ------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> ArgumentBuilder<CommandSourceStack, ?> arg(String name,
                                                             com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return com.mojang.brigadier.builder.RequiredArgumentBuilder
                .<CommandSourceStack, T>argument(name, type);
    }

    private static String str(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    private static int intArg(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, Integer.class);
    }

    private static double dbl(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, Double.class);
    }
}
