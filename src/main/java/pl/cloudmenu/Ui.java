package pl.cloudmenu;

import net.minecraft.client.gui.DrawContext;

/** Pomocnicze rysowanie: zaokrąglone prostokąty, chmurka, kolory. */
public final class Ui {
    private Ui() {
    }

    public record Palette(int panel, int card, int row, int rowHover, int line, int text, int muted) {
    }

    private static final Palette DARK =
            new Palette(0x12141A, 0x1B1E27, 0x252936, 0x2F3445, 0x363B4D, 0xE8EAF0, 0x8A91A3);
    private static final Palette LIGHT =
            new Palette(0xEEF1F6, 0xFFFFFF, 0xF0F3F8, 0xE2E7F0, 0xD3D9E6, 0x1B1E27, 0x6B7280);

    public static Palette palette(boolean light) {
        return light ? LIGHT : DARK;
    }

    public static int argb(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Biały albo ciemny tekst – zależnie od jasności tła. */
    public static int onColor(int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 160 ? 0x14161C : 0xFFFFFF;
    }

    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int dx = r - (int) Math.round(Math.sqrt(r * (double) r - d * d));
            ctx.fill(x + dx, y + i, x + w - dx, y + i + 1, color);
            ctx.fill(x + dx, y + h - i - 1, x + w - dx, y + h - i, color);
        }
        ctx.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Chmurka 28x18 px. Kolor podaj z pełnym alfa (np. Ui.opaque). */
    public static void cloud(DrawContext ctx, int x, int y, int color) {
        roundRect(ctx, x, y + 7, 28, 11, 5, color);
        roundRect(ctx, x + 3, y + 3, 12, 12, 6, color);
        roundRect(ctx, x + 10, y, 14, 14, 7, color);
    }
}
