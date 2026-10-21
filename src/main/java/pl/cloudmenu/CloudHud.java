package pl.cloudmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/** Mały HUD w lewym górnym rogu: FPS, ping, koordynaty. */
public final class CloudHud {
    private CloudHud() {
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CloudConfig c = CloudConfig.get();
        if (!c.hud || mc.player == null || mc.options.hudHidden) {
            return;
        }

        List<String> lines = new ArrayList<>();
        if (c.hudFps) {
            lines.add(mc.getCurrentFps() + " FPS");
        }
        if (c.hudPing && mc.getNetworkHandler() != null) {
            PlayerListEntry self = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (self != null) {
                lines.add(self.getLatency() + " ms");
            }
        }
        if (c.hudCoords) {
            lines.add(mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ());
        }
        if (lines.isEmpty()) {
            return;
        }

        int width = 0;
        for (String s : lines) {
            width = Math.max(width, mc.textRenderer.getWidth(s));
        }
        int x = 6;
        int y = 6;
        int w = width + 14;
        int h = lines.size() * 11 + 6;

        Ui.roundRect(ctx, x, y, w, h, 4, Ui.argb(170, 0x101216));
        Ui.roundRect(ctx, x, y + 3, 2, h - 6, 1, Ui.opaque(c.accent));
        int ty = y + 5;
        for (String s : lines) {
            ctx.drawText(mc.textRenderer, s, x + 8, ty, 0xFFFFFFFF, false);
            ty += 11;
        }
    }
}
