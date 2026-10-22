package pl.cloudmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

/**
 * Tracery: linie od celownika do innych graczy w załadowanym świecie.
 * Rysowane na nakładce 2D (rzutowanie pozycji gracza na ekran), działa w widoku z pierwszej osoby.
 */
public final class Tracers {
    private Tracers() {
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CloudConfig cfg = CloudConfig.get();
        if (!cfg.tracers || mc.player == null || mc.world == null || mc.options.hudHidden) {
            return;
        }
        if (!mc.options.getPerspective().isFirstPerson()) {
            return;
        }

        float tp = tickCounter.getTickProgress(false);
        Vec3d eye = mc.player.getLerpedEyePos(tp);
        double yaw = Math.toRadians(mc.player.getYaw(tp));
        double pitch = Math.toRadians(mc.player.getPitch(tp));

        // baza kamery: przód, prawo, góra
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double ux = -Math.sin(yaw) * Math.sin(pitch);
        double uy = Math.cos(pitch);
        double uz = Math.cos(yaw) * Math.sin(pitch);

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        double cx = sw / 2.0;
        double cy = sh / 2.0;
        double fov = Math.toRadians(mc.options.getFov().getValue());
        double scale = cy / Math.tan(fov / 2.0);

        int color = Ui.argb(230, cfg.accent);

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) {
                continue;
            }
            Vec3d pos = p.getLerpedPos(tp);
            double dx = pos.x - eye.x;
            double dy = pos.y + p.getHeight() * 0.5 - eye.y;
            double dz = pos.z - eye.z;

            double depth = dx * fx + dy * fy + dz * fz;
            double sideX = dx * rx + dz * rz;
            double sideY = dx * ux + dy * uy + dz * uz;

            double ex;
            double ey;
            if (depth > 0.05) {
                ex = cx + sideX / depth * scale;
                ey = cy - sideY / depth * scale;
            } else {
                // gracz za plecami: linia idzie w stronę krawędzi ekranu
                double bx = sideX;
                double by = -sideY;
                double len = Math.hypot(bx, by);
                if (len < 1e-4) {
                    bx = 0;
                    by = 1;
                    len = 1;
                }
                ex = cx + bx / len * 100000.0;
                ey = cy + by / len * 100000.0;
            }

            // przytnij koniec linii do ekranu
            double vecX = ex - cx;
            double vecY = ey - cy;
            double k = 1.0;
            double limX = cx - 3;
            double limY = cy - 3;
            if (Math.abs(vecX) > limX) {
                k = Math.min(k, limX / Math.abs(vecX));
            }
            if (Math.abs(vecY) > limY) {
                k = Math.min(k, limY / Math.abs(vecY));
            }
            int endX = (int) Math.round(cx + vecX * k);
            int endY = (int) Math.round(cy + vecY * k);

            line(ctx, (int) Math.round(cx), (int) Math.round(cy), endX, endY, color);
            ctx.fill(endX - 1, endY - 1, endX + 2, endY + 2, color);
        }
    }

    /** Linia z poziomych / pionowych odcinków (jeden fill na serię pikseli). */
    private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int n = Math.max(Math.abs(dx), Math.abs(dy));
        if (n == 0) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            int step = dx >= 0 ? 1 : -1;
            int runStart = x0;
            int runY = y0;
            for (int i = 1; i <= n; i++) {
                int x = x0 + step * i;
                int y = (int) Math.round(y0 + (double) dy * i / n);
                if (y != runY) {
                    hRun(ctx, runStart, x - step, runY, color);
                    runStart = x;
                    runY = y;
                }
            }
            hRun(ctx, runStart, x1, runY, color);
        } else {
            int step = dy >= 0 ? 1 : -1;
            int runStart = y0;
            int runX = x0;
            for (int i = 1; i <= n; i++) {
                int y = y0 + step * i;
                int x = (int) Math.round(x0 + (double) dx * i / n);
                if (x != runX) {
                    vRun(ctx, runX, runStart, y - step, color);
                    runStart = y;
                    runX = x;
                }
            }
            vRun(ctx, runX, runStart, y1, color);
        }
    }

    private static void hRun(DrawContext ctx, int xa, int xb, int y, int color) {
        ctx.fill(Math.min(xa, xb), y, Math.max(xa, xb) + 1, y + 1, color);
    }

    private static void vRun(DrawContext ctx, int x, int ya, int yb, int color) {
        ctx.fill(x, Math.min(ya, yb), x + 1, Math.max(ya, yb) + 1, color);
    }
}
