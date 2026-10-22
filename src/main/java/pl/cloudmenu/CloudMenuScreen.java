package pl.cloudmenu;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleConsumer;

/** Przezroczyste, przeciągane okno menu (klawisz P, zmienialny w zakładce Klawisze). */
public class CloudMenuScreen extends Screen {

    private enum Tab {
        PLAYERS("Gracze"),
        MODULES("Moduły"),
        APPEARANCE("Wygląd"),
        KEYS("Klawisze");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** Klikalny obszar zbudowany podczas rysowania klatki. */
    private record Hit(int x, int y, int w, int h, boolean scrolled, Runnable click, DoubleConsumer drag) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final int[] PRESETS = {0x58B4FF, 0x8B7BFF, 0xFF6FA8, 0x4ADE9B, 0xFFA94D, 0xF25F5C, 0xE5E7EB};
    private static final int COLUMNS = 3;
    private static final int TOP_BAR = 30;

    private final List<Hit> hits = new ArrayList<>();
    private Tab tab = Tab.PLAYERS;
    private int scroll;
    private int maxScroll;
    private int clipTop;
    private int clipBottom;
    private Hit dragging;
    private KeyBinding listening;
    private int hx;
    private int hy;
    private Ui.Palette pal;
    private int accent;
    private int alpha;
    private int rowAlpha;
    private boolean shadow;
    private String copied;
    private long copiedUntil;

    // okno
    private int px;
    private int py;
    private int pw;
    private int ph;
    private boolean draggingWin;
    private int dragDX;
    private int dragDY;

    public CloudMenuScreen() {
        super(Text.literal("Cloud Menu"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        hx = mouseX;
        hy = mouseY;
        CloudConfig cfg = CloudConfig.get();
        pal = Ui.palette(cfg.light);
        accent = cfg.accent;
        alpha = cfg.opacity;
        rowAlpha = (int) (alpha * 0.85);
        shadow = !cfg.light;

        // okno (bez przyciemniania reszty ekranu)
        pw = Math.min(430, width - 16);
        ph = Math.min(250, height - 16);
        px = cfg.winX < 0 ? (width - pw) / 2 : cfg.winX;
        py = cfg.winY < 0 ? (height - ph) / 2 : cfg.winY;
        px = Math.max(0, Math.min(px, width - pw));
        py = Math.max(0, Math.min(py, height - ph));

        Ui.roundRect(ctx, px, py, pw, ph, 8, Ui.argb(alpha, pal.panel()));

        // --- pasek górny: chmurka w lewym górnym rogu, zakładki, zamknij
        Ui.cloud(ctx, px + 10, py + 6, Ui.opaque(accent));
        bold(ctx, "Cloud", px + 44, py + 11, pal.text());

        int tx = px + 92;
        for (Tab t : Tab.values()) {
            int tw = textRenderer.getWidth(t.label) + 16;
            boolean active = t == tab;
            boolean hov = hover(tx, py + 6, tw, 18, false);
            if (active) {
                Ui.roundRect(ctx, tx, py + 6, tw, 18, 5, Ui.opaque(accent));
            } else if (hov) {
                Ui.roundRect(ctx, tx, py + 6, tw, 18, 5, Ui.argb(rowAlpha, pal.rowHover()));
            }
            text(ctx, t.label, tx + 8, py + 11, active ? Ui.onColor(accent) : pal.text());
            final Tab target = t;
            hits.add(new Hit(tx, py + 6, tw, 18, false, () -> {
                tab = target;
                scroll = 0;
                listening = null;
            }, null));
            tx += tw + 4;
        }

        int closeX = px + pw - 26;
        boolean closeHover = hover(closeX, py + 6, 18, 18, false);
        if (closeHover) {
            Ui.roundRect(ctx, closeX, py + 6, 18, 18, 5, Ui.opaque(0xF25F5C));
        }
        text(ctx, "x", closeX + 6, py + 11, closeHover ? 0xFFFFFF : pal.muted());
        hits.add(new Hit(closeX, py + 6, 18, 18, false, () -> close(), null));

        if (tab == Tab.PLAYERS) {
            ClientPlayNetworkHandler net = client == null ? null : client.getNetworkHandler();
            String note = (net == null ? 0 : net.getPlayerList().size()) + " online";
            if (copied != null && System.currentTimeMillis() < copiedUntil) {
                note = "Skopiowano: " + copied;
            }
            text(ctx, note, closeX - 8 - textRenderer.getWidth(note), py + 11, pal.muted());
        }
        ctx.fill(px + 10, py + TOP_BAR, px + pw - 10, py + TOP_BAR + 1, Ui.argb(110, pal.line()));

        // --- zawartość
        int cx = px + 10;
        int cy = py + TOP_BAR + 6;
        int cw = pw - 20;
        int ch = ph - TOP_BAR - 6 - 8;
        clipTop = cy;
        clipBottom = cy + ch;

        ctx.enableScissor(cx, clipTop, cx + cw, clipBottom);
        int x = cx;
        int w = cw - 8;
        int y = clipTop - scroll;
        int used = switch (tab) {
            case PLAYERS -> drawPlayers(ctx, x, y, w);
            case MODULES -> drawModules(ctx, x, y, w);
            case APPEARANCE -> drawAppearance(ctx, x, y, w);
            case KEYS -> drawKeys(ctx, x, y, w);
        };
        ctx.disableScissor();

        maxScroll = Math.max(0, used - (clipBottom - clipTop));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (maxScroll > 0) {
            int trackH = clipBottom - clipTop;
            int thumbH = Math.max(18, trackH * trackH / (trackH + maxScroll));
            int thumbY = clipTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            Ui.roundRect(ctx, cx + cw - 4, thumbY, 3, thumbH, 1, Ui.argb(170, pal.muted()));
        }
    }

    // ------------------------------------------------------------------ zakładki

    private int drawPlayers(DrawContext ctx, int x, int y, int w) {
        ClientPlayNetworkHandler net = client.getNetworkHandler();
        if (net == null || client.player == null) {
            text(ctx, "Brak połączenia z serwerem.", x, y + 4, pal.muted());
            return 20;
        }
        PlayerListHud tabList = client.inGameHud.getPlayerListHud();
        List<PlayerListEntry> list = new ArrayList<>(net.getPlayerList());
        list.sort(Comparator.comparing((PlayerListEntry e) -> tabList.getPlayerName(e).getString().toLowerCase()));
        PlayerListEntry self = net.getPlayerListEntry(client.player.getUuid());

        if (list.isEmpty()) {
            text(ctx, "Nikogo tu nie ma.", x, y + 4, pal.muted());
            return 20;
        }

        int gap = 6;
        int cardW = (w - gap * (COLUMNS - 1)) / COLUMNS;
        int cardH = 30;
        for (int i = 0; i < list.size(); i++) {
            PlayerListEntry e = list.get(i);
            int col = i % COLUMNS;
            int rowIdx = i / COLUMNS;
            int rx = x + col * (cardW + gap);
            int ry = y + rowIdx * (cardH + gap);

            String plain = tabList.getPlayerName(e).getString();
            row(ctx, rx, ry, cardW, cardH, () -> copy(plain));
            PlayerSkinDrawer.draw(ctx, e.getSkinTextures(), rx + 6, ry + 7, 16);

            String shown = textRenderer.trimToWidth(plain, cardW - 34);
            text(ctx, shown, rx + 28, ry + 6, e == self ? accent : pal.text());

            int ping = e.getLatency();
            String ps = ping + " ms";
            int pc = ping < 0 ? pal.muted() : ping < 80 ? 0x4ADE80 : ping < 160 ? 0xFACC15 : 0xF87171;
            text(ctx, ps, rx + 28, ry + 17, pc);
        }
        int rows = (list.size() + COLUMNS - 1) / COLUMNS;
        return rows * (cardH + gap);
    }

    private int drawModules(DrawContext ctx, int x, int y, int w) {
        CloudConfig c = CloudConfig.get();
        int u = 0;
        u += section(ctx, x, y + u, "Gameplay");
        u += toggleRow(ctx, x, y + u, w, "Tracery", "Linie od celownika do graczy (na serwerach często zabronione)",
                c.tracers, () -> {
                    c.tracers = !c.tracers;
                    CloudConfig.save();
                });
        u += toggleRow(ctx, x, y + u, w, "Fullbright", "Widzisz w ciemności (efekt tylko u Ciebie)",
                c.fullbright, () -> {
                    c.fullbright = !c.fullbright;
                    CloudConfig.save();
                });
        u += toggleRow(ctx, x, y + u, w, "Auto sprint", "Biegniesz sam, gdy idziesz do przodu",
                c.autoSprint, () -> {
                    c.autoSprint = !c.autoSprint;
                    CloudConfig.save();
                });
        u += section(ctx, x, y + u, "HUD");
        u += toggleRow(ctx, x, y + u, w, "Pokaż HUD", "Mała nakładka w lewym górnym rogu",
                c.hud, () -> {
                    c.hud = !c.hud;
                    CloudConfig.save();
                });
        u += toggleRow(ctx, x, y + u, w, "FPS", null, c.hudFps, () -> {
            c.hudFps = !c.hudFps;
            CloudConfig.save();
        });
        u += toggleRow(ctx, x, y + u, w, "Ping", null, c.hudPing, () -> {
            c.hudPing = !c.hudPing;
            CloudConfig.save();
        });
        u += toggleRow(ctx, x, y + u, w, "Koordynaty", null, c.hudCoords, () -> {
            c.hudCoords = !c.hudCoords;
            CloudConfig.save();
        });
        return u;
    }

    private int drawAppearance(DrawContext ctx, int x, int y, int w) {
        CloudConfig c = CloudConfig.get();
        int u = 0;

        u += section(ctx, x, y + u, "Kolor motywu");
        for (int i = 0; i < PRESETS.length; i++) {
            int col = PRESETS[i];
            int sxp = x + i * 30 + 2;
            int syp = y + u + 2;
            if ((c.accent & 0xFFFFFF) == col) {
                Ui.roundRect(ctx, sxp - 2, syp - 2, 26, 26, 8, Ui.opaque(pal.text()));
            }
            Ui.roundRect(ctx, sxp, syp, 22, 22, 6, Ui.opaque(col));
            hits.add(new Hit(sxp, syp, 22, 22, true, () -> {
                c.accent = col;
                CloudConfig.save();
            }, null));
        }
        u += 34;

        u += slider(ctx, x, y + u, w, "R", ((c.accent >> 16) & 255) / 255.0,
                String.valueOf((c.accent >> 16) & 255), f -> {
                    c.accent = (c.accent & 0x00FFFF) | (((int) Math.round(f * 255)) << 16);
                }, 0xF25F5C);
        u += slider(ctx, x, y + u, w, "G", ((c.accent >> 8) & 255) / 255.0,
                String.valueOf((c.accent >> 8) & 255), f -> {
                    c.accent = (c.accent & 0xFF00FF) | (((int) Math.round(f * 255)) << 8);
                }, 0x4ADE80);
        u += slider(ctx, x, y + u, w, "B", (c.accent & 255) / 255.0,
                String.valueOf(c.accent & 255), f -> {
                    c.accent = (c.accent & 0xFFFF00) | ((int) Math.round(f * 255));
                }, 0x58B4FF);
        text(ctx, String.format("HEX  #%06X", c.accent & 0xFFFFFF), x + 2, y + u + 2, pal.muted());
        u += 18;

        u += section(ctx, x, y + u, "Okno");
        u += slider(ctx, x, y + u, w, "A", (c.opacity - 40) / 215.0,
                Math.round((c.opacity / 255f) * 100) + "%", f -> {
                    c.opacity = 40 + (int) Math.round(f * 215);
                }, pal.muted());
        u += toggleRow(ctx, x, y + u, w, "Jasny motyw", null, c.light, () -> {
            c.light = !c.light;
            CloudConfig.save();
        });
        u += 4;
        text(ctx, "Okno przeciągniesz za górny pasek.", x + 2, y + u, pal.muted());
        u += 12;
        text(ctx, "Mniejsza wartość A = bardziej przezroczyste.", x + 2, y + u, pal.muted());
        u += 14;
        return u;
    }

    private int drawKeys(DrawContext ctx, int x, int y, int w) {
        int u = 0;
        u += keyRow(ctx, x, y + u, w, "Otwórz menu", CloudKeys.OPEN);
        u += keyRow(ctx, x, y + u, w, "Zoom (przytrzymaj)", CloudKeys.ZOOM);
        u += keyRow(ctx, x, y + u, w, "Tracery (przełącz)", CloudKeys.TRACERS);
        u += keyRow(ctx, x, y + u, w, "Fullbright (przełącz)", CloudKeys.FULLBRIGHT);
        u += keyRow(ctx, x, y + u, w, "Auto sprint (przełącz)", CloudKeys.SPRINT);
        u += 4;
        text(ctx, "Kliknij przycisk i naciśnij klawisz.", x + 2, y + u, pal.muted());
        text(ctx, "ESC = brak klawisza, lewy przycisk myszy = anuluj.", x + 2, y + u + 12, pal.muted());
        text(ctx, "Te same klawisze są w Opcje > Sterowanie.", x + 2, y + u + 24, pal.muted());
        return u + 38;
    }

    // ------------------------------------------------------------------ widgety

    private boolean hover(int x, int y, int w, int h, boolean scrolled) {
        boolean in = hx >= x && hx < x + w && hy >= y && hy < y + h;
        return in && (!scrolled || (hy >= clipTop && hy < clipBottom));
    }

    private boolean row(DrawContext ctx, int x, int y, int w, int h, Runnable click) {
        boolean hov = hover(x, y, w, h, true);
        Ui.roundRect(ctx, x, y, w, h, 5, Ui.argb(rowAlpha, hov ? pal.rowHover() : pal.row()));
        if (click != null) {
            hits.add(new Hit(x, y, w, h, true, click, null));
        }
        return hov;
    }

    private int section(DrawContext ctx, int x, int y, String label) {
        text(ctx, label.toUpperCase(), x + 2, y + 3, pal.muted());
        return 16;
    }

    private int toggleRow(DrawContext ctx, int x, int y, int w, String label, String desc,
                          boolean on, Runnable toggle) {
        int h = desc == null ? 24 : 32;
        row(ctx, x, y, w, h, toggle);
        text(ctx, label, x + 10, desc == null ? y + 8 : y + 6, pal.text());
        if (desc != null) {
            text(ctx, textRenderer.trimToWidth(desc, w - 60), x + 10, y + 18, pal.muted());
        }
        int tx = x + w - 36;
        int tyy = y + h / 2 - 6;
        Ui.roundRect(ctx, tx, tyy, 26, 12, 6, on ? Ui.opaque(accent) : Ui.opaque(pal.line()));
        Ui.roundRect(ctx, on ? tx + 16 : tx + 2, tyy + 2, 8, 8, 4, 0xFFFFFFFF);
        return h + 4;
    }

    private int slider(DrawContext ctx, int x, int y, int w, String label, double frac,
                       String valueText, DoubleConsumer onChange, int fillColor) {
        frac = Math.max(0, Math.min(1, frac));
        text(ctx, label, x + 4, y + 7, pal.text());
        int tx = x + 22;
        int tw = w - 22 - 44;
        Ui.roundRect(ctx, tx, y + 8, tw, 6, 3, Ui.opaque(pal.line()));
        Ui.roundRect(ctx, tx, y + 8, Math.max(6, (int) (tw * frac)), 6, 3, Ui.opaque(fillColor));
        int kx = tx + (int) (tw * frac) - 5;
        Ui.roundRect(ctx, Math.max(tx - 2, kx), y + 5, 10, 12, 5, 0xFFFFFFFF);
        text(ctx, valueText, x + w - 6 - textRenderer.getWidth(valueText), y + 7, pal.muted());
        hits.add(new Hit(tx - 5, y, tw + 10, 22, true, null, onChange));
        return 24;
    }

    private int keyRow(DrawContext ctx, int x, int y, int w, String label, KeyBinding kb) {
        boolean isListening = listening == kb;
        row(ctx, x, y, w, 26, null);
        text(ctx, label, x + 10, y + 9, pal.text());
        String t = isListening ? "Naciśnij klawisz..." : keyName(kb);
        int bw = Math.max(70, textRenderer.getWidth(t) + 16);
        int bx = x + w - bw - 6;
        int by = y + 4;
        Ui.roundRect(ctx, bx, by, bw, 18, 5, isListening ? Ui.opaque(accent) : Ui.opaque(pal.line()));
        text(ctx, t, bx + (bw - textRenderer.getWidth(t)) / 2, by + 5,
                isListening ? Ui.onColor(accent) : pal.text());
        hits.add(new Hit(bx, by, bw, 18, true, () -> listening = isListening ? null : kb, null));
        return 30;
    }

    private String keyName(KeyBinding kb) {
        return kb.isUnbound() ? "Brak" : kb.getBoundKeyLocalizedText().getString();
    }

    private void text(DrawContext ctx, String s, int x, int y, int rgb) {
        ctx.drawText(textRenderer, s, x, y, Ui.opaque(rgb), shadow);
    }

    private void bold(DrawContext ctx, String s, int x, int y, int rgb) {
        ctx.drawText(textRenderer, Text.literal(s).formatted(Formatting.BOLD), x, y, Ui.opaque(rgb), shadow);
    }

    private void copy(String s) {
        client.keyboard.setClipboard(s);
        copied = s;
        copiedUntil = System.currentTimeMillis() + 2000;
    }

    // ------------------------------------------------------------------ wejście

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listening != null) {
            if (click.button() == 0) {
                listening = null;
            } else {
                bind(InputUtil.Type.MOUSE.createFromCode(click.button()));
            }
            return true;
        }
        if (click.button() == 0) {
            double mx = click.x();
            double my = click.y();
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (!h.contains(mx, my)) {
                    continue;
                }
                if (h.scrolled() && (my < clipTop || my >= clipBottom)) {
                    continue;
                }
                if (h.click() != null) {
                    h.click().run();
                }
                if (h.drag() != null) {
                    dragging = h;
                    dragTo(h, mx);
                }
                return true;
            }
            // przeciąganie okna za górny pasek
            if (mx >= px && mx < px + pw && my >= py && my < py + TOP_BAR) {
                draggingWin = true;
                dragDX = (int) (mx - px);
                dragDY = (int) (my - py);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging != null) {
            dragTo(dragging, click.x());
            return true;
        }
        if (draggingWin) {
            CloudConfig cfg = CloudConfig.get();
            cfg.winX = Math.max(0, Math.min((int) (click.x() - dragDX), width - pw));
            cfg.winY = Math.max(0, Math.min((int) (click.y() - dragDY), height - ph));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            CloudConfig.save();
            return true;
        }
        if (draggingWin) {
            draggingWin = false;
            CloudConfig.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 20)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                bind(InputUtil.UNKNOWN_KEY);
            } else {
                bind(InputUtil.Type.KEYSYM.createFromCode(input.key()));
            }
            return true;
        }
        // ten sam klawisz zamyka menu
        if (!CloudKeys.OPEN.isUnbound()
                && CloudKeys.OPEN.getBoundKeyTranslationKey()
                .equals(InputUtil.Type.KEYSYM.createFromCode(input.key()).getTranslationKey())) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private void dragTo(Hit h, double mouseX) {
        double frac = (mouseX - (h.x() + 5)) / (double) (h.w() - 10);
        h.drag().accept(Math.max(0, Math.min(1, frac)));
    }

    private void bind(InputUtil.Key key) {
        listening.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        client.options.write();
        listening = null;
    }
}
