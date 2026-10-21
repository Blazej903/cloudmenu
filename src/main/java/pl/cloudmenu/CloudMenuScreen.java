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

/** Menu otwierane klawiszem P (zmienialny w zakładce Klawisze). */
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
    private String copied;
    private long copiedUntil;

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

        ctx.fill(0, 0, width, height, 0x66000000);

        int pw = Math.min(470, width - 16);
        int ph = Math.min(292, height - 16);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;

        Ui.roundRect(ctx, px - 2, py - 2, pw + 4, ph + 4, 10, 0x44000000);
        Ui.roundRect(ctx, px, py, pw, ph, 8, Ui.argb(alpha, pal.panel()));

        // --- panel boczny z chmurką w lewym górnym rogu
        int sx = px + 8;
        int sy = py + 8;
        int sw = 112;
        int sh = ph - 16;
        Ui.roundRect(ctx, sx, sy, sw, sh, 6, Ui.argb(alpha, pal.card()));
        Ui.cloud(ctx, sx + 10, sy + 10, Ui.opaque(accent));
        bold(ctx, "Cloud", sx + 44, sy + 10, pal.text());
        text(ctx, "menu", sx + 44, sy + 21, pal.muted());
        ctx.fill(sx + 8, sy + 36, sx + sw - 8, sy + 37, Ui.opaque(pal.line()));

        int ty = sy + 44;
        for (Tab t : Tab.values()) {
            boolean active = t == tab;
            boolean hov = hover(sx + 6, ty, sw - 12, 22, false);
            if (active) {
                Ui.roundRect(ctx, sx + 6, ty, sw - 12, 22, 5, Ui.opaque(accent));
            } else if (hov) {
                Ui.roundRect(ctx, sx + 6, ty, sw - 12, 22, 5, Ui.opaque(pal.rowHover()));
            }
            text(ctx, t.label, sx + 16, ty + 7, active ? Ui.onColor(accent) : pal.text());
            final Tab target = t;
            hits.add(new Hit(sx + 6, ty, sw - 12, 22, false, () -> {
                tab = target;
                scroll = 0;
                listening = null;
            }, null));
            ty += 25;
        }
        text(ctx, "Menu: " + keyName(CloudKeys.OPEN), sx + 10, sy + sh - 14, pal.muted());

        // --- karta z zawartością
        int cx = sx + sw + 8;
        int cy = py + 8;
        int cw = px + pw - 8 - cx;
        int ch = ph - 16;
        Ui.roundRect(ctx, cx, cy, cw, ch, 6, Ui.argb(alpha, pal.card()));
        bold(ctx, tab.label, cx + 12, cy + 10, pal.text());

        String note = "";
        if (tab == Tab.PLAYERS) {
            ClientPlayNetworkHandler net = client == null ? null : client.getNetworkHandler();
            note = (net == null ? 0 : net.getPlayerList().size()) + " online";
            if (copied != null && System.currentTimeMillis() < copiedUntil) {
                note = "Skopiowano: " + copied;
            }
        }
        if (!note.isEmpty()) {
            text(ctx, note, cx + cw - 12 - textRenderer.getWidth(note), cy + 10, pal.muted());
        }
        ctx.fill(cx + 10, cy + 25, cx + cw - 10, cy + 26, Ui.opaque(pal.line()));

        clipTop = cy + 30;
        clipBottom = cy + ch - 6;
        ctx.enableScissor(cx, clipTop, cx + cw, clipBottom);
        int x = cx + 10;
        int w = cw - 24;
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
            Ui.roundRect(ctx, cx + cw - 8, thumbY, 3, thumbH, 1, Ui.argb(170, pal.muted()));
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

        int used = 0;
        for (PlayerListEntry e : list) {
            Text name = tabList.getPlayerName(e);
            String plain = name.getString();
            int ry = y + used;
            row(ctx, x, ry, w, 26, () -> copy(plain));
            PlayerSkinDrawer.draw(ctx, e.getSkinTextures(), x + 6, ry + 5, 16);
            ctx.drawText(textRenderer, name, x + 28, ry + 9, Ui.opaque(pal.text()), false);
            if (e == self) {
                text(ctx, "(Ty)", x + 28 + textRenderer.getWidth(name) + 6, ry + 9, accent);
            }
            int ping = e.getLatency();
            String ps = ping + " ms";
            int pc = ping < 0 ? pal.muted() : ping < 80 ? 0x4ADE80 : ping < 160 ? 0xFACC15 : 0xF87171;
            text(ctx, ps, x + w - 8 - textRenderer.getWidth(ps), ry + 9, pc);
            used += 30;
        }
        if (list.isEmpty()) {
            text(ctx, "Nikogo tu nie ma.", x, y + 4, pal.muted());
            return 20;
        }
        return used;
    }

    private int drawModules(DrawContext ctx, int x, int y, int w) {
        CloudConfig c = CloudConfig.get();
        int u = 0;
        u += section(ctx, x, y + u, "Gameplay");
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
            int sxp = x + i * 30;
            int syp = y + u;
            if ((c.accent & 0xFFFFFF) == col) {
                Ui.roundRect(ctx, sxp - 2, syp - 2, 26, 26, 8, Ui.opaque(pal.text()));
            }
            Ui.roundRect(ctx, sxp, syp, 22, 22, 6, Ui.opaque(col));
            hits.add(new Hit(sxp, syp, 22, 22, true, () -> {
                c.accent = col;
                CloudConfig.save();
            }, null));
        }
        u += 32;

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

        u += section(ctx, x, y + u, "Panel");
        u += slider(ctx, x, y + u, w, "A", (c.opacity - 120) / 135.0,
                Math.round((c.opacity / 255f) * 100) + "%", f -> {
                    c.opacity = 120 + (int) Math.round(f * 135);
                }, pal.muted());
        u += toggleRow(ctx, x, y + u, w, "Jasny motyw", null, c.light, () -> {
            c.light = !c.light;
            CloudConfig.save();
        });
        return u;
    }

    private int drawKeys(DrawContext ctx, int x, int y, int w) {
        int u = 0;
        u += keyRow(ctx, x, y + u, w, "Otwórz menu", CloudKeys.OPEN);
        u += keyRow(ctx, x, y + u, w, "Zoom (przytrzymaj)", CloudKeys.ZOOM);
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
        Ui.roundRect(ctx, x, y, w, h, 5, Ui.argb(alpha, hov ? pal.rowHover() : pal.row()));
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
            text(ctx, desc, x + 10, y + 18, pal.muted());
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
        ctx.drawText(textRenderer, s, x, y, Ui.opaque(rgb), false);
    }

    private void bold(DrawContext ctx, String s, int x, int y, int rgb) {
        ctx.drawText(textRenderer, Text.literal(s).formatted(Formatting.BOLD), x, y, Ui.opaque(rgb), false);
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
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging != null) {
            dragTo(dragging, click.x());
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
