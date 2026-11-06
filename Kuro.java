import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Kuro Launcher - Vanilla / Fabric / Quilt, Modrinth mod installer, offline accounts. */
public class Kuro {
    // ---------- theme ----------
    static final Color PANEL = new Color(20, 20, 28), PANEL2 = new Color(30, 30, 42), BORDER = new Color(48, 46, 64),
            FG = new Color(236, 236, 244), MUTED = new Color(140, 140, 160), ACC = new Color(150, 90, 255),
            ACC2 = new Color(105, 70, 235), RED = new Color(255, 100, 115);
    static final Path ROOT = Paths.get(System.getProperty("user.home"), ".kuro");
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    static final String OS = System.getProperty("os.name").toLowerCase().contains("win") ? "windows"
            : System.getProperty("os.name").toLowerCase().contains("mac") ? "osx" : "linux";
    static final String[] LOADERS = {"Vanilla", "Fabric", "Quilt"};

    static Color mix(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t), (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }
    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }
    static Font font(int style, float size) { return new Font("Segoe UI", style, 12).deriveFont(size); }
    static JLabel lbl(String t, float size, boolean bold, Color c) {
        JLabel l = new JLabel(t); l.setFont(font(bold ? Font.BOLD : Font.PLAIN, size)); l.setForeground(c); return l;
    }
    static <T extends JComponent> T fixed(T c, int w, int h) {
        Dimension d = new Dimension(w, h);
        c.setPreferredSize(d); c.setMinimumSize(d); c.setMaximumSize(d); c.setAlignmentX(0.5f);
        return c;
    }
    static void drawCentered(Graphics2D g, String s, int w, int h) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (w - fm.stringWidth(s)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
    }
    static void darkScroll(JScrollPane sp) {
        sp.setOpaque(false); sp.getViewport().setOpaque(false); sp.setBorder(null);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            protected void configureScrollBarColors() { thumbColor = new Color(90, 90, 112); trackColor = new Color(0, 0, 0, 0); }
            protected JButton createDecreaseButton(int o) { return zero(); }
            protected JButton createIncreaseButton(int o) { return zero(); }
            JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
            protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
            protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = aa(g); g2.setColor(thumbColor); g2.fillRoundRect(r.x + 1, r.y, r.width - 2, r.height, 6, 6);
            }
        });
    }
    static JPanel vbox() { JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setOpaque(false); return p; }
    static JScrollPane listScroll(JPanel list) {
        JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false); wrap.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap); darkScroll(sp); return sp;
    }

    // ---------- animation ----------
    static class Anim {
        float v, t; final Timer tm;
        Anim(Runnable r) {
            tm = new Timer(15, e -> {
                v += (t - v) * 0.25f;
                if (Math.abs(t - v) < 0.003f) { v = t; ((Timer) e.getSource()).stop(); }
                r.run();
            });
        }
        void to(float x) { t = x; if (!tm.isRunning()) tm.start(); }
        void set(float x) { v = t = x; }
    }

    // ---------- components ----------
    static class KButton extends JComponent {
        String text; boolean accent, down; Runnable click; final Anim hov = new Anim(this::repaint);
        KButton(String t, boolean accent, Runnable r) {
            text = t; this.accent = accent; click = r;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(130, 40));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { if (isEnabled()) hov.to(1); }
                public void mouseExited(MouseEvent e) { hov.to(0); down = false; repaint(); }
                public void mousePressed(MouseEvent e) { if (isEnabled()) { down = true; repaint(); } }
                public void mouseReleased(MouseEvent e) {
                    boolean in = down && contains(e.getPoint()); down = false; repaint();
                    if (in && isEnabled() && click != null) click.run();
                }
            });
        }
        public void setEnabled(boolean b) {
            super.setEnabled(b);
            setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint();
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); float sc = down ? 0.97f : 1f;
            g.translate(w * (1 - sc) / 2, h * (1 - sc) / 2); g.scale(sc, sc);
            Shape s = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
            if (!isEnabled()) { g.setColor(new Color(34, 34, 44)); g.fill(s); }
            else if (accent) {
                g.setPaint(new GradientPaint(0, 0, mix(ACC, new Color(190, 135, 255), hov.v), w, h, mix(ACC2, new Color(140, 100, 255), hov.v)));
                g.fill(s);
            } else {
                g.setColor(mix(PANEL2, new Color(48, 46, 66), hov.v)); g.fill(s);
                g.setColor(BORDER); g.setStroke(new BasicStroke(1f)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 14, 14));
            }
            g.setColor(!isEnabled() ? MUTED : accent ? Color.WHITE : FG);
            g.setFont(font(Font.BOLD, 14)); drawCentered(g, text, w, h);
        }
    }

    static class KField extends JTextField {
        final String hint; final Anim foc = new Anim(this::repaint);
        KField(String hint) {
            this.hint = hint; setOpaque(false); setForeground(FG); setCaretColor(ACC);
            setSelectionColor(new Color(150, 90, 255, 110)); setFont(font(Font.PLAIN, 14));
            setBorder(new EmptyBorder(0, 14, 0, 14)); setPreferredSize(new Dimension(200, 42));
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { foc.to(1); }
                public void focusLost(FocusEvent e) { foc.to(0); }
            });
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); Shape s = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(PANEL2); g.fill(s);
            g.setColor(mix(BORDER, ACC, foc.v)); g.setStroke(new BasicStroke(1.4f)); g.draw(s);
            super.paintComponent(g0);
            if (getText().isEmpty()) {
                g.setColor(MUTED); g.setFont(getFont()); FontMetrics fm = g.getFontMetrics();
                g.drawString(hint, 14, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }

    static class KPick extends JComponent {
        String text = "Yükleniyor..."; final List<String> items = new ArrayList<>(); java.util.function.Consumer<String> on;
        final Anim hov = new Anim(this::repaint);
        KPick() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov.to(1); }
                public void mouseExited(MouseEvent e) { hov.to(0); }
                public void mousePressed(MouseEvent e) { popup(); }
            });
        }
        void popup() {
            if (items.isEmpty()) return;
            JList<String> list = new JList<>(items.toArray(new String[0]));
            list.setBackground(PANEL2); list.setForeground(FG); list.setSelectionBackground(ACC2); list.setSelectionForeground(Color.WHITE);
            list.setFixedCellHeight(30); list.setFont(font(Font.PLAIN, 14));
            list.setCellRenderer(new DefaultListCellRenderer() {
                public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                    JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, f);
                    c.setBorder(new EmptyBorder(0, 12, 0, 12)); return c;
                }
            });
            list.setSelectedValue(text, true);
            JScrollPane sp = new JScrollPane(list); darkScroll(sp); sp.setOpaque(true); sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(PANEL2);
            sp.setPreferredSize(new Dimension(getWidth(), Math.min(280, items.size() * 30 + 4)));
            JPopupMenu pm = new JPopupMenu(); pm.setBorder(BorderFactory.createLineBorder(BORDER)); pm.setBackground(PANEL2); pm.add(sp);
            list.addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    String v = list.getSelectedValue(); pm.setVisible(false);
                    if (v != null) { text = v; repaint(); if (on != null) on.accept(v); }
                }
            });
            pm.show(this, 0, getHeight() + 4);
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(mix(PANEL2, new Color(42, 40, 58), hov.v)); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            g.setColor(BORDER); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 14, 14));
            g.setColor(FG); g.setFont(font(Font.PLAIN, 14)); g.drawString(text, 14, (h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
            g.setColor(MUTED); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolyline(new int[]{w - 26, w - 20, w - 14}, new int[]{h / 2 - 2, h / 2 + 3, h / 2 - 2}, 3);
        }
    }

    static class Seg extends JComponent {
        final String[] opts; int sel; IntConsumer on; final Anim pos = new Anim(this::repaint);
        Seg(String... o) {
            opts = o; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { set(Math.min(opts.length - 1, e.getX() * opts.length / Math.max(1, getWidth())), true); } });
        }
        void set(int i, boolean fire) { sel = i; pos.to(i); if (fire && on != null) on.accept(i); }
        void init(int i) { sel = i; pos.set(i); repaint(); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); float sw = w / (float) opts.length;
            g.setColor(PANEL2); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            g.setPaint(new GradientPaint(0, 0, ACC, w, h, ACC2));
            g.fill(new RoundRectangle2D.Float(4 + pos.v * sw, 4, sw - 8, h - 8, 11, 11));
            g.setFont(font(Font.BOLD, 14));
            for (int i = 0; i < opts.length; i++) {
                float d = Math.min(1f, Math.abs(pos.v - i));
                g.setColor(mix(Color.WHITE, MUTED, d));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(opts[i], i * sw + (sw - fm.stringWidth(opts[i])) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }

    static class KProgress extends JComponent {
        final Anim a = new Anim(this::repaint);
        KProgress() { setPreferredSize(new Dimension(100, 8)); }
        void set(float v) { a.to(v); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(PANEL2); g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            if (a.v > 0.001f) {
                g.setPaint(new GradientPaint(0, 0, ACC2, w, 0, new Color(200, 140, 255)));
                g.fill(new RoundRectangle2D.Float(0, 0, Math.max(h, w * a.v), h, h, h));
            }
        }
    }

    static class KSlider extends JComponent {
        final int min, max; int val; IntConsumer on;
        KSlider(int min, int max, int val) {
            this.min = min; this.max = max; this.val = val; setPreferredSize(new Dimension(360, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { fromX(e.getX()); }
                public void mouseDragged(MouseEvent e) { fromX(e.getX()); }
            };
            addMouseListener(m); addMouseMotionListener(m);
        }
        void fromX(int x) {
            double f = Math.max(0, Math.min(1, (x - 10) / (double) Math.max(1, getWidth() - 20)));
            int nv = min + (int) Math.round(f * (max - min));
            if (nv != val) { val = nv; repaint(); if (on != null) on.accept(val); }
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            float x = 10 + (w - 20) * (val - min) / (float) (max - min);
            g.setColor(PANEL2); g.fill(new RoundRectangle2D.Float(10, h / 2f - 3, w - 20, 6, 6, 6));
            g.setPaint(new GradientPaint(0, 0, ACC2, w, 0, ACC)); g.fill(new RoundRectangle2D.Float(10, h / 2f - 3, x - 10, 6, 6, 6));
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Float(x - 9, h / 2f - 9, 18, 18));
            g.setColor(ACC); g.setStroke(new BasicStroke(2.5f)); g.draw(new Ellipse2D.Float(x - 9, h / 2f - 9, 18, 18));
        }
    }

    static class Row extends JPanel {
        final Anim hov = new Anim(this::repaint);
        Row(String title, String sub, JComponent... right) {
            super(new BorderLayout(10, 0)); setOpaque(false); setBorder(new EmptyBorder(6, 14, 6, 10));
            setPreferredSize(new Dimension(10, 58)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 58)); setAlignmentX(0f);
            JPanel l = new JPanel(new GridLayout(sub == null ? 1 : 2, 1)); l.setOpaque(false);
            l.add(lbl(title, 14, true, FG)); if (sub != null) l.add(lbl(sub, 12, false, MUTED));
            add(l, BorderLayout.CENTER);
            if (right.length > 0) {
                JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6)); r.setOpaque(false);
                for (JComponent c : right) r.add(c);
                add(r, BorderLayout.EAST);
            }
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov.to(1); }
                public void mouseExited(MouseEvent e) { if (!contains(e.getPoint())) hov.to(0); }
            });
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); g.setColor(mix(PANEL, PANEL2, hov.v));
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
        }
    }

    static class Pager extends JPanel {
        BufferedImage snap; final Anim fade = new Anim(this::repaint);
        Pager() { super(new BorderLayout()); setOpaque(false); }
        void show(JComponent c) {
            if (getWidth() > 0 && getHeight() > 0 && getComponentCount() > 0) {
                snap = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = snap.createGraphics(); paintChildren(g); g.dispose();
                fade.set(1); fade.to(0);
            }
            removeAll(); add(c, BorderLayout.CENTER); revalidate(); repaint();
        }
        public void paint(Graphics g0) {
            super.paint(g0);
            if (snap != null) {
                if (fade.v > 0.01f) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, Math.max(0f, fade.v))));
                    g.drawImage(snap, 0, 0, null); g.dispose();
                } else snap = null;
            }
        }
    }

    static class Bg extends JPanel {
        final long t0 = System.nanoTime();
        Bg() { super(new BorderLayout()); setOpaque(true); new Timer(33, e -> repaint()).start(); }
        void blob(Graphics2D g, double x, double y, float r, Color c) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), r, new float[]{0f, 1f},
                    new Color[]{c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fill(new Rectangle2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); double t = (System.nanoTime() - t0) / 1e9;
            g.setPaint(new GradientPaint(0, 0, new Color(11, 10, 17), 0, h, new Color(6, 6, 10))); g.fillRect(0, 0, w, h);
            blob(g, w * (0.22 + 0.12 * Math.sin(t * 0.4)), h * (0.18 + 0.1 * Math.cos(t * 0.3)), 460, new Color(130, 75, 255, 62));
            blob(g, w * (0.82 + 0.1 * Math.cos(t * 0.35)), h * (0.88 + 0.08 * Math.sin(t * 0.5)), 520, new Color(70, 60, 210, 48));
        }
    }

    static class TitleBar extends JComponent {
        final JFrame f; int hov = -1; Point press;
        TitleBar(JFrame f) {
            this.f = f; setPreferredSize(new Dimension(0, 40));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    press = e.getPoint(); int b = btn(e.getX());
                    if (b == 1) f.setState(Frame.ICONIFIED); else if (b == 2) System.exit(0);
                }
                public void mouseDragged(MouseEvent e) {
                    if (press != null && btn(press.x) < 0) { Point p = e.getLocationOnScreen(); f.setLocation(p.x - press.x, p.y - press.y); }
                }
                public void mouseMoved(MouseEvent e) { hov = btn(e.getX()); repaint(); }
                public void mouseExited(MouseEvent e) { hov = -1; repaint(); }
            };
            addMouseListener(m); addMouseMotionListener(m);
        }
        int btn(int x) { int w = getWidth(); return x >= w - 46 ? 2 : x >= w - 92 ? 1 : -1; }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(MUTED); g.setFont(font(Font.BOLD, 12)); g.drawString("KURO LAUNCHER", 18, 25);
            if (hov == 2) { g.setColor(new Color(255, 80, 90, 200)); g.fillRect(w - 46, 0, 46, h); }
            else if (hov == 1) { g.setColor(new Color(255, 255, 255, 25)); g.fillRect(w - 92, 0, 46, h); }
            g.setColor(FG); g.setStroke(new BasicStroke(1.4f));
            g.drawLine(w - 76, 20, w - 62, 20);
            g.drawLine(w - 30, 14, w - 16, 26); g.drawLine(w - 30, 26, w - 16, 14);
        }
    }

    static class Sidebar extends JComponent {
        final String[] items = {"Oyna", "Modlar", "Ayarlar"}; int sel = 0, hover = -1; String user = "";
        final Anim pos = new Anim(this::repaint); IntConsumer on; Runnable logout;
        Sidebar() {
            setPreferredSize(new Dimension(230, 0)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    int i = idx(e.getY());
                    if (i >= 0) select(i); else if (e.getY() > getHeight() - 84 && logout != null) logout.run();
                }
                public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            });
            addMouseMotionListener(new MouseMotionAdapter() { public void mouseMoved(MouseEvent e) { hover = idx(e.getY()); repaint(); } });
        }
        int idx(int y) { int i = (y - 110) / 52; return (y >= 110 && i < items.length && (y - 110) % 52 < 46) ? i : -1; }
        void select(int i) { sel = i; pos.to(i); if (on != null) on.accept(i); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(new Color(255, 255, 255, 8)); g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 255, 255, 14)); g.fillRect(w - 1, 0, 1, h);
            g.setFont(font(Font.BOLD, 34)); g.setPaint(new GradientPaint(0, 30, new Color(200, 150, 255), 120, 70, ACC)); g.drawString("KURO", 28, 66);
            g.setFont(font(Font.BOLD, 10)); g.setColor(MUTED); g.drawString("L A U N C H E R", 30, 84);
            g.setPaint(new GradientPaint(16, 0, new Color(150, 90, 255, 70), w - 16, 0, new Color(105, 70, 235, 25)));
            g.fill(new RoundRectangle2D.Float(16, 110 + pos.v * 52, w - 32, 46, 14, 14));
            g.setColor(ACC); g.fill(new RoundRectangle2D.Float(16, 120 + pos.v * 52, 4, 26, 4, 4));
            g.setFont(font(Font.BOLD, 15));
            for (int i = 0; i < items.length; i++) {
                float d = Math.min(1f, Math.abs(pos.v - i));
                Color c = mix(FG, hover == i ? new Color(200, 200, 220) : MUTED, d);
                g.setColor(c); g.drawString(items[i], 44, 110 + i * 52 + 29);
            }
            g.setColor(new Color(255, 255, 255, 14)); g.fillRect(16, h - 84, w - 32, 1);
            g.setPaint(new GradientPaint(0, 0, ACC, 40, 40, ACC2)); g.fill(new Ellipse2D.Float(20, h - 66, 40, 40));
            g.setColor(Color.WHITE); g.setFont(font(Font.BOLD, 17));
            String ini = user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase();
            FontMetrics fm = g.getFontMetrics(); g.drawString(ini, 20 + (40 - fm.stringWidth(ini)) / 2, h - 66 + (40 + fm.getAscent() - fm.getDescent()) / 2);
            g.setColor(FG); g.setFont(font(Font.BOLD, 14)); g.drawString(user, 70, h - 49);
            g.setColor(MUTED); g.setFont(font(Font.PLAIN, 12)); g.drawString("Çıkış yap", 70, h - 32);
        }
    }

    // ---------- state ----------
    static Map<String, Object> cfg = new LinkedHashMap<>();
    JFrame frame; Pager screen = new Pager(), pages = new Pager(); Sidebar side = new Sidebar();
    JPanel app, playPage, setPage; JLabel status = lbl(" ", 13, false, MUTED), subtitle = lbl(" ", 14, false, MUTED);
    KProgress progress = new KProgress(); KButton play; JTextArea log = new JTextArea();
    KPick verPick = new KPick(); Seg loaderSeg = new Seg(LOADERS);
    JPanel installedList = vbox(), resultList = vbox(); JLabel modInfo = lbl(" ", 13, false, MUTED);
    Map<String, String> versionUrls = new LinkedHashMap<>();
    String account, selVer; int selLoader;

    static void loadCfg() {
        try { cfg = Json.obj(Files.readString(ROOT.resolve("settings.json"))); } catch (Exception e) { cfg = new LinkedHashMap<>(); }
    }
    static void saveCfg() {
        try { Files.createDirectories(ROOT); Files.writeString(ROOT.resolve("settings.json"), Json.str(cfg)); } catch (Exception e) { }
    }
    static String cs(String k, String d) { Object o = cfg.get(k); return o == null ? d : o.toString(); }
    static int ci(String k, int d) { Object o = cfg.get(k); return o instanceof Number ? ((Number) o).intValue() : d; }
    static List<String> accounts() {
        List<String> l = new ArrayList<>(); Object o = cfg.get("accounts");
        if (o instanceof List) for (Object x : (List<?>) o) l.add(x.toString());
        return l;
    }
    static void setAccounts(List<String> l) { cfg.put("accounts", new ArrayList<Object>(l)); saveCfg(); }

    public static void main(String[] a) {
        loadCfg();
        SwingUtilities.invokeLater(() -> new Kuro().start());
    }

    Path instDir() { return ROOT.resolve("instances").resolve(selVer + "-" + LOADERS[selLoader].toLowerCase()); }

    void start() {
        frame = new JFrame("Kuro Launcher");
        frame.setUndecorated(true); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1020, 650); frame.setLocationRelativeTo(null);
        frame.setShape(new RoundRectangle2D.Double(0, 0, 1020, 650, 26, 26));
        Bg bg = new Bg(); bg.add(new TitleBar(frame), BorderLayout.NORTH);
        screen.setBorder(new EmptyBorder(0, 0, 0, 0)); bg.add(screen, BorderLayout.CENTER);
        frame.setContentPane(bg);

        selLoader = Math.max(0, Arrays.asList(LOADERS).indexOf(cs("loader", "Fabric")));
        selVer = cs("version", "1.21.4");
        loaderSeg.init(selLoader);
        loaderSeg.on = i -> { selLoader = i; cfg.put("loader", LOADERS[i]); saveCfg(); updateInfo(); refreshMods(); };
        verPick.text = selVer;
        verPick.on = v -> { selVer = v; cfg.put("version", v); saveCfg(); updateInfo(); refreshMods(); };

        buildApp();
        account = cs("last", null);
        if (account != null) { side.user = account; screen.add(app, BorderLayout.CENTER); updateInfo(); }
        else screen.add(buildLogin(), BorderLayout.CENTER);
        frame.setVisible(true);
        new Thread(this::loadVersions).start();
    }

    void updateInfo() {
        SwingUtilities.invokeLater(() -> {
            subtitle.setText("Hazır mısın, " + (account == null ? "" : account) + "?");
            modInfo.setText(selVer + "  -  " + LOADERS[selLoader]);
        });
    }

    // ---------- yenilenen modern giriş (login) ekranı ----------
    JComponent buildLogin() {
        JPanel center = new JPanel(new GridBagLayout()); center.setOpaque(false);
        JPanel card = new JPanel() {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = aa(g0);
                g.setColor(new Color(18, 18, 26, 235));
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                g.setColor(new Color(255, 255, 255, 25));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 24, 24));
            }
        };
        card.setOpaque(false); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS)); 
        card.setBorder(new EmptyBorder(32, 40, 32, 40));
        card.setPreferredSize(new Dimension(420, 520));

        // Logo ve Hoş Geldin Başlığı
        JLabel logo = lbl("KURO", 42, true, ACC); logo.setAlignmentX(0.5f);
        JLabel sub = lbl("Kuro Launcher'a Hoş Geldin", 14, true, FG); sub.setAlignmentX(0.5f);
        JLabel desc = lbl("Oynamak için bir hesap seç veya yeni oluştur", 12, false, MUTED); desc.setAlignmentX(0.5f);
        
        card.add(logo); 
        card.add(Box.createVerticalStrut(4));
        card.add(sub);
        card.add(Box.createVerticalStrut(2));
        card.add(desc); 
        card.add(Box.createVerticalStrut(20));

        // Kayıtlı Hesaplar listesi (Varsa scroll içinde düzgün görünür)
        JPanel accs = vbox(); accs.setAlignmentX(0.5f);
        List<String> savedAccs = accounts();
        if (!savedAccs.isEmpty()) {
            JPanel accWrap = new JPanel(); accWrap.setLayout(new BoxLayout(accWrap, BoxLayout.Y_AXIS)); accWrap.setOpaque(false);
            for (String n : savedAccs) {
                KButton x = new KButton("✕", false, null); x.setPreferredSize(new Dimension(30, 30));
                Row r = new Row(n, "Çevrimdışı Hesap", x);
                x.click = () -> { List<String> l = accounts(); l.remove(n); setAccounts(l); screen.show(buildLogin()); };
                r.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                r.addMouseListener(new MouseAdapter() { 
                    public void mouseReleased(MouseEvent e) { 
                        if (x.getBounds().contains(e.getPoint())) return; // silme tuşuna basıldıysa giriş yapmasın
                        if (r.contains(e.getPoint())) login(n); 
                    } 
                });
                accWrap.add(r); accWrap.add(Box.createVerticalStrut(6));
            }
            JScrollPane accSp = new JScrollPane(accWrap); darkScroll(accSp);
            accSp.setPreferredSize(new Dimension(340, Math.min(120, savedAccs.size() * 64)));
            accSp.setMaximumSize(new Dimension(340, 120));
            card.add(accSp);
            card.add(Box.createVerticalStrut(14));
        }

        // Yeni hesap oluşturma alanı
        KField name = fixed(new KField("Kullanıcı Adı (Örn: Steve)"), 340, 44);
        JLabel err = lbl(" ", 12, false, RED); err.setAlignmentX(0.5f);
        Runnable go = () -> {
            String n = name.getText().trim();
            if (!n.matches("[A-Za-z0-9_]{3,16}")) { err.setText("3-16 karakter (Harf, rakam, _)"); return; }
            login(n);
        };
        name.addActionListener(e -> go.run());
        
        KButton enter = fixed(new KButton("Hemen Başla", true, go), 340, 44);
        KButton ms = fixed(new KButton("Microsoft ile Giriş (Çok Yakında)", false, null), 340, 44); ms.setEnabled(false);
        
        card.add(name); 
        card.add(Box.createVerticalStrut(4)); 
        card.add(err);
        card.add(Box.createVerticalStrut(8)); 
        card.add(enter); 
        card.add(Box.createVerticalStrut(8)); 
        card.add(ms);
        
        center.add(card);
        return center;
    }

    void login(String n) {
        List<String> l = accounts(); if (!l.contains(n)) l.add(n); setAccounts(l);
        account = n; cfg.put("last", n); saveCfg(); side.user = n; updateInfo();
        screen.show(app);
    }

    void logout() {
        account = null; cfg.remove("last"); saveCfg(); side.user = ""; screen.show(buildLogin());
    }

    // ---------- app ----------
    void buildApp() {
        app = new JPanel(new BorderLayout()); app.setOpaque(false);
        side.on = i -> { if (i == 0) pages.show(playPage); else if (i == 1) pages.show(buildMods()); else pages.show(setPage); };
        side.logout = this::logout;
        app.add(side, BorderLayout.WEST);
        playPage = buildPlay(); setPage = buildSettings();
        pages.add(playPage, BorderLayout.CENTER);
        app.add(pages, BorderLayout.CENTER);
    }

    JPanel pageBase() {
        JPanel p = new JPanel(new BorderLayout(0, 16)); p.setOpaque(false); p.setBorder(new EmptyBorder(8, 34, 28, 34)); return p;
    }

    JPanel buildPlay() {
        JPanel p = pageBase();
        JPanel head = vbox();
        head.add(lbl("Oyna", 30, true, FG)); head.add(subtitle); head.add(Box.createVerticalStrut(22));
        head.add(lbl("SÜRÜM", 11, true, MUTED)); head.add(Box.createVerticalStrut(6));
        JPanel vr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); vr.setOpaque(false); vr.setAlignmentX(0f);
        verPick.setPreferredSize(new Dimension(260, 44)); vr.add(verPick); head.add(vr); head.add(Box.createVerticalStrut(18));
        head.add(lbl("MOD YÜKLEYİCİ (MOD LOADER)", 11, true, MUTED)); head.add(Box.createVerticalStrut(6));
        JPanel sr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); sr.setOpaque(false); sr.setAlignmentX(0f);
        loaderSeg.setPreferredSize(new Dimension(380, 44)); sr.add(loaderSeg); head.add(sr);
        p.add(head, BorderLayout.NORTH);

        log.setEditable(false); log.setOpaque(false); log.setForeground(new Color(170, 170, 190)); log.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(log); darkScroll(sp); sp.setBorder(new EmptyBorder(8, 12, 8, 4));
        JPanel logBox = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = aa(g0); g.setColor(new Color(12, 12, 18, 200)); g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            }
        };
        logBox.setOpaque(false); logBox.add(sp);
        p.add(logBox, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(16, 0)); south.setOpaque(false);
        JPanel pr = new JPanel(new GridLayout(2, 1, 0, 8)); pr.setOpaque(false);
        pr.add(status); pr.add(progress);
        south.add(pr, BorderLayout.CENTER);
        play = new KButton("OYNA", true, () -> new Thread(this::launch).start());
        play.setPreferredSize(new Dimension(200, 54)); south.add(play, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    JPanel buildSettings() {
        JPanel p = pageBase(); JPanel b = vbox();
        b.add(lbl("Ayarlar", 30, true, FG)); b.add(Box.createVerticalStrut(24));
        int ram = ci("ram", 4);
        JLabel rl = lbl("BELLEK (RAM): " + ram + " GB", 11, true, MUTED); b.add(rl); b.add(Box.createVerticalStrut(8));
        KSlider sl = new KSlider(1, 16, ram); sl.setAlignmentX(0f); sl.setMaximumSize(new Dimension(420, 30));
        sl.on = v -> { cfg.put("ram", (long) v); saveCfg(); rl.setText("BELLEK (RAM): " + v + " GB"); };
        b.add(sl); b.add(Box.createVerticalStrut(22));
        b.add(lbl("EK JVM ARGÜMANLARI", 11, true, MUTED)); b.add(Box.createVerticalStrut(8));
        KField jvm = new KField("örnek: -XX:+UseG1GC"); jvm.setText(cs("jvm", "")); jvm.setAlignmentX(0f); jvm.setMaximumSize(new Dimension(520, 44));
        jvm.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { cfg.put("jvm", jvm.getText()); saveCfg(); } });
        b.add(jvm); b.add(Box.createVerticalStrut(26));
        JPanel br = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); br.setOpaque(false); br.setAlignmentX(0f);
        KButton o1 = new KButton("Oyun Klasörünü Aç", false, () -> open(ROOT.resolve("instances"))); o1.setPreferredSize(new Dimension(190, 42));
        br.add(o1); b.add(br);
        p.add(b, BorderLayout.NORTH);
        return p;
    }

    static void open(Path d) {
        try { Files.createDirectories(d); Desktop.getDesktop().open(d.toFile()); } catch (Exception e) { }
    }

    // ---------- mods ----------
    JPanel buildMods() {
        JPanel p = pageBase();
        JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false);
        JPanel t = vbox(); t.add(lbl("Modlar", 30, true, FG)); t.add(modInfo); head.add(t, BorderLayout.CENTER);
        JPanel hb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8)); hb.setOpaque(false);
        KButton add = new KButton("Dosya Ekle", false, this::addModFile); add.setPreferredSize(new Dimension(120, 40));
        KButton of = new KButton("Klasörü Aç", false, () -> open(instDir().resolve("mods"))); of.setPreferredSize(new Dimension(120, 40));
        hb.add(add); hb.add(of); head.add(hb, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);

        JPanel cols = new JPanel(new GridLayout(1, 2, 18, 0)); cols.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout(0, 10)); left.setOpaque(false);
        left.add(lbl("YÜKLÜ MODLAR", 11, true, MUTED), BorderLayout.NORTH); left.add(listScroll(installedList), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout(0, 10)); right.setOpaque(false);
        JPanel sr = new JPanel(new BorderLayout(8, 0)); sr.setOpaque(false);
        KField q = new KField("Modrinth'te ara..."); KButton go = new KButton("Ara", true, null); go.setPreferredSize(new Dimension(80, 42));
        go.click = () -> search(q.getText().trim()); q.addActionListener(e -> search(q.getText().trim()));
        sr.add(q, BorderLayout.CENTER); sr.add(go, BorderLayout.EAST);
        right.add(sr, BorderLayout.NORTH); right.add(listScroll(resultList), BorderLayout.CENTER);
        cols.add(left); cols.add(right); p.add(cols, BorderLayout.CENTER);
        updateInfo(); refreshMods();
        return p;
    }

    void refreshMods() {
        installedList.removeAll();
        if (selLoader == 0) {
            installedList.add(new Row("Vanilla'da mod çalışmaz", "Fabric veya Quilt seç", new JComponent[0]));
        } else {
            Path dir = instDir().resolve("mods");
            try (var s = Files.exists(dir) ? Files.list(dir) : java.util.stream.Stream.<Path>empty()) {
                List<Path> files = new ArrayList<>(); s.forEach(files::add); Collections.sort(files);
                for (Path f : files) {
                    String n = f.getFileName().toString();
                    boolean dis = n.endsWith(".disabled");
                    if (!n.endsWith(".jar") && !dis) continue;
                    KButton tg = new KButton(dis ? "Aç" : "Kapat", false, null); tg.setPreferredSize(new Dimension(62, 34));
                    KButton rm = new KButton("Sil", false, null); rm.setPreferredSize(new Dimension(50, 34));
                    tg.click = () -> { try { Files.move(f, f.resolveSibling(dis ? n.substring(0, n.length() - 9) : n + ".disabled")); } catch (Exception e) { } refreshMods(); };
                    rm.click = () -> { try { Files.deleteIfExists(f); } catch (Exception e) { } refreshMods(); };
                    String shown = dis ? n.substring(0, n.length() - 9) : n;
                    installedList.add(new Row(shown.length() > 30 ? shown.substring(0, 29) + "..." : shown,
                            (Files.size(f) / 1024) + " KB" + (dis ? "  -  kapalı" : ""), tg, rm));
                    installedList.add(Box.createVerticalStrut(6));
                }
            } catch (Exception e) { }
            if (installedList.getComponentCount() == 0) installedList.add(new Row("Henüz mod yok", "Sağdan Modrinth'te ara", new JComponent[0]));
        }
        installedList.revalidate(); installedList.repaint();
    }

    void addModFile() {
        if (selLoader == 0) return;
        JFileChooser fc = new JFileChooser(); fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Mod (.jar)", "jar"));
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path dir = instDir().resolve("mods"); Files.createDirectories(dir);
            for (File f : fc.getSelectedFiles()) Files.copy(f.toPath(), dir.resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { log("Hata: " + e); }
        refreshMods();
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    void search(String q) {
        if (selLoader == 0) return;
        resultList.removeAll(); resultList.add(new Row("Araniyor...", null, new JComponent[0])); resultList.revalidate(); resultList.repaint();
        new Thread(() -> {
            try {
                String facets = "[["versions:" + selVer + ""],["categories:" + LOADERS[selLoader].toLowerCase() + ""],["project_type:mod"]]";
                Map<String, Object> r = Json.obj(get("https://api.modrinth.com/v2/search?limit=20&query=" + enc(q) + "&facets=" + enc(facets)));
                List<Object> hits = Json.arr(r.get("hits"));
                SwingUtilities.invokeLater(() -> {
                    resultList.removeAll();
                    for (Object h : hits) {
                        Map<String, Object> m = Json.obj(h);
                        String title = String.valueOf(m.get("title")), desc = String.valueOf(m.get("description")), id = String.valueOf(m.get("project_id"));
                        KButton ins = new KButton("Yükle", true, null); ins.setPreferredSize(new Dimension(74, 34));
                        ins.click = () -> installAsync(id, title, ins);
                        resultList.add(new Row(title, desc.length() > 44 ? desc.substring(0, 43) + "..." : desc, ins));
                        resultList.add(Box.createVerticalStrut(6));
                    }
                    if (hits.isEmpty()) resultList.add(new Row("Sonuç yok", "Bu sürüm/yükleyici için mod bulunamadı", new JComponent[0]));
                    resultList.revalidate(); resultList.repaint();
                });
            } catch (Exception e) { log("Arama hatası: " + e); }
        }).start();
    }

    void installAsync(String id, String title, KButton btn) {
        btn.setEnabled(false); btn.text = "...";
        new Thread(() -> {
            try {
                Path dir = instDir().resolve("mods"); Files.createDirectories(dir);
                installMod(id, dir, 0, new HashSet<>());
                SwingUtilities.invokeLater(() -> { btn.text = "Tamam"; btn.repaint(); refreshMods(); });
            } catch (Exception e) {
                log("Mod yüklenemedi (" + title + "): " + e);
                SwingUtilities.invokeLater(() -> { btn.text = "Hata"; btn.repaint(); });
            }
        }).start();
    }

    void installMod(String pid, Path dir, int depth, Set<String> seen) throws Exception {
        if (!seen.add(pid) || depth > 3) return;
        String url = "https://api.modrinth.com/v2/project/" + pid + "/version?loaders=" + enc("["" + LOADERS[selLoader].toLowerCase() + ""]")
                + "&game_versions=" + enc("["" + selVer + ""]");
        List<Object> vs = Json.arr(Json.parse(get(url)));
        if (vs.isEmpty()) throw new Exception("uyumlu sürüm yok");
        Map<String, Object> v = Json.obj(vs.get(0));
        if (v.get("dependencies") != null)
            for (Object o : Json.arr(v.get("dependencies"))) {
                Map<String, Object> d = Json.obj(o);
                if ("required".equals(d.get("dependency_type")) && d.get("project_id") != null) installMod(String.valueOf(d.get("project_id")), dir, depth + 1, seen);
            }
        Map<String, Object> file = null;
        for (Object o : Json.arr(v.get("files"))) { Map<String, Object> f = Json.obj(o); if (file == null || Boolean.TRUE.equals(f.get("primary"))) file = f; }
        if (file == null) throw new Exception("dosya yok");
        download((String) file.get("url"), dir.resolve((String) file.get("filename")));
        log("Yüklendi: " + file.get("filename"));
    }

    // ---------- helpers ----------
    void log(String s) {
        SwingUtilities.invokeLater(() -> { log.append(s + "
"); log.setCaretPosition(log.getDocument().getLength()); });
    }
    void prog(int v, int max, String t) {
        SwingUtilities.invokeLater(() -> { progress.set(max == 0 ? 0 : v / (float) max); status.setText(t); });
    }
    static String get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Kuro-Launcher/1.0").build(), HttpResponse.BodyHandlers.ofString()).body();
    }
    static void download(String url, Path dst) throws Exception {
        if (Files.exists(dst) && Files.size(dst) > 0) return;
        Files.createDirectories(dst.getParent());
        Path tmp = Paths.get(dst + ".part");
        HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Kuro-Launcher/1.0").build(), HttpResponse.BodyHandlers.ofFile(tmp));
        Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    void loadVersions() {
        try {
            Map<String, Object> m = Json.obj(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));
            for (Object o : Json.arr(m.get("versions"))) {
                Map<String, Object> v = Json.obj(o);
                if ("release".equals(v.get("type"))) versionUrls.put((String) v.get("id"), (String) v.get("url"));
            }
            SwingUtilities.invokeLater(() -> {
                verPick.items.clear();
                verPick.items.addAll(versionUrls.keySet());
                if (!versionUrls.containsKey(selVer) && !verPick.items.isEmpty()) { selVer = verPick.items.get(0); }
                verPick.text = selVer; verPick.repaint(); updateInfo();
            });
        } catch (Exception e) { log("Sürüm listesi alınamadı: " + e); }
    }

    // ---------- launch ----------
    void launch() {
        SwingUtilities.invokeLater(() -> play.setEnabled(false));
        try {
            String mc = selVer;
            if (mc == null || !versionUrls.containsKey(mc)) throw new Exception("Sürüm seçilmedi");
            String name = account == null ? "Player" : account;
            prog(0, 1, "Sürüm bilgisi indiriliyor...");
            Map<String, Object> meta = Json.obj(get(versionUrls.get(mc)));
            Path verDir = ROOT.resolve("versions").resolve(mc);
            Files.createDirectories(verDir);

            List<Map<String, Object>> libs = new ArrayList<>();
            for (Object o : Json.arr(meta.get("libraries"))) libs.add(Json.obj(o));
            String mainClass = (String) meta.get("mainClass");
            List<String> jvmExtra = new ArrayList<>(), gameExtra = new ArrayList<>();
            collectArgs(meta, jvmExtra, gameExtra);

            if (selLoader > 0) {
                String base = selLoader == 1 ? "https://meta.fabricmc.net/v2" : "https://meta.quiltmc.org/v3";
                log(LOADERS[selLoader] + " profili indiriliyor...");
                List<Object> loaders = Json.arr(Json.parse(get(base + "/versions/loader/" + mc)));
                if (loaders.isEmpty()) throw new Exception("Bu sürüm için " + LOADERS[selLoader] + " yok");
                Map<String, Object> pick = Json.obj(loaders.get(0));
                for (Object o : loaders) { Map<String, Object> l = Json.obj(Json.obj(o).get("loader")); if (Boolean.TRUE.equals(l.get("stable"))) { pick = Json.obj(o); break; } }
                String lv = (String) Json.obj(pick.get("loader")).get("version");
                Map<String, Object> fp = Json.obj(get(base + "/versions/loader/" + mc + "/" + lv + "/profile/json"));
                mainClass = (String) fp.get("mainClass");
                for (Object o : Json.arr(fp.get("libraries"))) libs.add(0, Json.obj(o));
                collectArgs(fp, jvmExtra, gameExtra);
            }

            List<Path> cp = new ArrayList<>();
            List<String[]> jobs = new ArrayList<>();
            for (Map<String, Object> lib : libs) {
                if (!allowed(lib.get("rules"))) continue;
                Map<String, Object> dl = lib.get("downloads") == null ? null : Json.obj(lib.get("downloads"));
                String url, rel;
                if (dl != null && dl.get("artifact") != null) {
                    Map<String, Object> a = Json.obj(dl.get("artifact"));
                    url = (String) a.get("url"); rel = (String) a.get("path");
                } else if (lib.get("url") != null) {
                    String[] c = ((String) lib.get("name")).split(":");
                    rel = c[0].replace('.', '/') + "/" + c[1] + "/" + c[2] + "/" + c[1] + "-" + c[2] + ".jar";
                    url = lib.get("url") + rel;
                } else continue;
                Path dst = ROOT.resolve("libraries").resolve(rel);
                jobs.add(new String[]{url, dst.toString()});
                cp.add(dst);
            }
            Map<String, Object> client = Json.obj(Json.obj(meta.get("downloads")).get("client"));
            Path clientJar = verDir.resolve(mc + ".jar");
            jobs.add(new String[]{(String) client.get("url"), clientJar.toString()});

            Map<String, Object> ai = Json.obj(meta.get("assetIndex"));
            String assetId = (String) ai.get("id");
            Path idx = ROOT.resolve("assets/indexes").resolve(assetId + ".json");
            download((String) ai.get("url"), idx);
            Map<String, Object> objs = Json.obj(Json.obj(Json.parse(Files.readString(idx))).get("objects"));
            for (Object o : objs.values()) {
                String h = (String) Json.obj(o).get("hash");
                jobs.add(new String[]{"https://resources.download.minecraft.net/" + h.substring(0, 2) + "/" + h,
                        ROOT.resolve("assets/objects").resolve(h.substring(0, 2)).resolve(h).toString()});
            }

            log("İndiriliyor: " + jobs.size() + " dosya");
            ExecutorService ex = Executors.newFixedThreadPool(16);
            AtomicInteger done = new AtomicInteger();
            List<Future<?>> fs = new ArrayList<>();
            for (String[] j : jobs) fs.add(ex.submit(() -> {
                try { download(j[0], Paths.get(j[1])); } catch (Exception e) { log("Hata: " + j[0]); }
                int d = done.incrementAndGet();
                if (d % 15 == 0 || d == jobs.size()) prog(d, jobs.size(), "İndiriliyor " + d + " / " + jobs.size());
            }));
            for (Future<?> f : fs) f.get();
            ex.shutdown();

            cp.add(clientJar);
            StringBuilder cps = new StringBuilder();
            for (Path p : new LinkedHashSet<>(cp)) cps.append(p).append(File.pathSeparator);
            Path game = instDir(); Files.createDirectories(game.resolve("mods"));

            Map<String, String> vars = new HashMap<>();
            vars.put("auth_player_name", name); vars.put("version_name", mc);
            vars.put("game_directory", game.toString()); vars.put("assets_root", ROOT.resolve("assets").toString());
            vars.put("assets_index_name", assetId);
            vars.put("auth_uuid", UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()).toString().replace("-", ""));
            vars.put("auth_access_token", "0"); vars.put("clientid", "0"); vars.put("auth_xuid", "0");
            vars.put("user_type", "legacy"); vars.put("version_type", "Kuro");
            vars.put("natives_directory", ROOT.resolve("natives").toString());
            vars.put("launcher_name", "Kuro"); vars.put("launcher_version", "2.0");
            vars.put("classpath", cps.toString());

            List<String> cmd = new ArrayList<>();
            cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
            cmd.add("-Xmx" + ci("ram", 4) + "G");
            for (String s : cs("jvm", "").trim().split("\s+")) if (!s.isEmpty()) cmd.add(s);
            for (String s : jvmExtra) cmd.add(sub(s, vars));
            if (!jvmExtra.contains("-cp")) { cmd.add("-cp"); cmd.add(cps.toString()); }
            cmd.add(mainClass);
            for (String s : gameExtra) cmd.add(sub(s, vars));

            prog(1, 1, "Oyun çalışıyor");
            log("Başlatılıyor: " + mc + " (" + LOADERS[selLoader] + ")");
            Process pr = new ProcessBuilder(cmd).directory(game.toFile()).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) log(l);
            }
            log("Oyun kapandı (kod " + pr.waitFor() + ")");
            prog(0, 1, "Hazır");
        } catch (Exception e) {
            log("HATA: " + e); prog(0, 1, "Hata oluştu, logu kontrol et");
        } finally {
            SwingUtilities.invokeLater(() -> play.setEnabled(true));
        }
    }

    static String sub(String s, Map<String, String> v) {
        for (Map.Entry<String, String> e : v.entrySet()) s = s.replace("${" + e.getKey() + "}", e.getValue());
        return s;
    }

    void collectArgs(Map<String, Object> meta, List<String> jvm, List<String> game) {
        if (meta.get("arguments") == null) return;
        Map<String, Object> a = Json.obj(meta.get("arguments"));
        for (String k : new String[]{"jvm", "game"}) {
            List<String> out = k.equals("jvm") ? jvm : game;
            if (a.get(k) == null) continue;
            for (Object o : Json.arr(a.get(k))) {
                if (o instanceof String) { out.add((String) o); continue; }
                if (k.equals("game")) continue;
                Map<String, Object> m = Json.obj(o);
                if (!allowed(m.get("rules"))) continue;
                Object v = m.get("value");
                if (v instanceof String) out.add((String) v);
                else for (Object x : Json.arr(v)) out.add((String) x);
            }
        }
    }

    static boolean allowed(Object rules) {
        if (rules == null) return true;
        String sys = System.getProperty("os.arch").toLowerCase();
        boolean arm = sys.contains("aarch64") || sys.contains("arm"), x64 = sys.contains("amd64") || sys.contains("x86_64");
        boolean ok = false;
        for (Object o : Json.arr(rules)) {
            Map<String, Object> r = Json.obj(o);
            boolean match = r.get("features") == null;
            if (r.get("os") != null) {
                Map<String, Object> os = Json.obj(r.get("os"));
                if (os.get("name") != null && !os.get("name").equals(OS)) match = false;
                Object ar = os.get("arch");
                if ("x86".equals(ar) && (x64 || arm)) match = false;
                if ("arm64".equals(ar) && !arm) match = false;
            }
            if (match) ok = "allow".equals(r.get("action"));
        }
        return ok;
    }
}

/** Tiny JSON parser/serializer (no external dependencies). */
class Json {
    final String s; int i;
    Json(String s) { this.s = s; }
    static Object parse(String s) { return new Json(s).val(); }
    @SuppressWarnings("unchecked") static Map<String, Object> obj(Object o) { return o instanceof String ? (Map<String, Object>) parse((String) o) : (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static List<Object> arr(Object o) { return (List<Object>) o; }

    void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    Object val() {
        ws();
        char c = s.charAt(i);
        if (c == '{') {
            Map<String, Object> m = new LinkedHashMap<>(); i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) { ws(); String k = str(); ws(); i++; m.put(k, val()); ws(); if (s.charAt(i++) == '}') return m; }
        }
        if (c == '[') {
            List<Object> l = new ArrayList<>(); i++; ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) { l.add(val()); ws(); if (s.charAt(i++) == ']') return l; }
        }
        if (c == '"') return str();
        if (s.startsWith("true", i)) { i += 4; return true; }
        if (s.startsWith("false", i)) { i += 5; return false; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(st, i);
        return n.contains(".") || n.contains("e") || n.contains("E") ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n);
    }
    String str() {
        StringBuilder b = new StringBuilder(); i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('
'); break; case 't': b.append('	'); break;
                    case 'r': b.append(''); break; case 'b': b.append(''); break;
                    case 'f': b.append(''); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
    }
    @SuppressWarnings("unchecked")
    static String str(Object o) {
        if (o == null) return "null";
        if (o instanceof String) return """ + ((String) o).replace("\", "\\").replace(""", "\"").replace("
", "\n") + """;
        if (o instanceof Map) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) j.add(str(e.getKey()) + ":" + str(e.getValue()));
            return j.toString();
        }
        if (o instanceof List) {
            StringJoiner j = new StringJoiner(",", "[", "]");
            for (Object x : (List<Object>) o) j.add(str(x));
            return j.toString();
        }
        return o.toString();
    }
}