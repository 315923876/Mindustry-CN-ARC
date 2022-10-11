package mindustry.arcModule.toolpack;

import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Pixmaps;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Schematic;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.logic.CanvasBlock;
import mindustry.world.blocks.logic.CanvasBlock.*;

import static mindustry.Vars.*;
import static mindustry.content.Blocks.canvas;
import static mindustry.content.Blocks.sorter;

public class picToMindustry {

    Pixmap oriImage, image, Cimage;
    Integer closest = null;
    Table tTable;
    Fi originFile;

    int[] palette;
    int canvasSize;

    float scale = 1f;
    float[] scaleList = {0.02f, 0.05f, 0.1f, 0.15f, 0.2f, 0.25f, 0.3f, 0.4f, 0.5f, 0.65f, 0.8f, 1f, 1.25f, 1.5f, 2f, 3f, 5f};

    public picToMindustry() {
        CanvasBlock canva = (CanvasBlock) Blocks.canvas;
        palette = canva.palette;
        canvasSize = canva.canvasSize;
        ptDialog().show();
    }

    public Dialog ptDialog() {
        Dialog pt = new BaseDialog("arc-图片转换器");
        pt.cont.table(t -> {
            t.add("选择并导入图片，可将其转成画板、像素画或是逻辑画").padBottom(20f).row();
            t.button("[cyan]选择图片", () -> {
                Vars.platform.showFileChooser(false, "png", file -> {
                    try {
                        originFile = file;
                        byte[] bytes = file.readBytes();
                        oriImage = new Pixmap(bytes);
                        rebuilt();
                        if (oriImage.width > 500 || oriImage.height > 500)
                            ui.arcInfo("[orange]警告：图片可能过大，请尝试压缩图片", 5);
                    } catch (Throwable e) {
                        ui.arcInfo("读取图片失败，请尝试更换图片\n" + e);
                    }
                });
            }).size(240, 50);
        }).padBottom(20f).row();
        pt.cont.table(t -> {
            t.add("缩放: \uE815 ");
            Label zoom = t.add(scale + "").padRight(20f).get();
            t.slider(0, scaleList.length - 1, 1, 11, s -> {
                scale = scaleList[(int) s];
                zoom.setText(scale + "");
                rebuilt();
            }).width(200f);
        }).padBottom(20f).row();
        pt.cont.table(a -> tTable = a);
        pt.addCloseButton();
        return pt;
    }

    private String formatNumber(int number) {
        return formatNumber(number, 1f);
    }

    private String formatNumber(int number, float alert) {
        if (number >= 500 * alert) return "[red]" + number + "[]";
        else if (number >= 200 * alert) return "[orange]" + number + "[]";
        else return number + "";
    }

    private void rebuilt() {
        image = Pixmaps.scale(oriImage, scale);
        tTable.clear();
        tTable.table(t -> {
            t.add("名称").color(getThemeColor()).padRight(25f).padBottom(10f);
            t.add(originFile.name()).padBottom(10f).row();
            t.add("大小").color(getThemeColor()).padRight(25f);
            t.add(formatNumber(image.width) + "\uE815" + formatNumber(image.height));
        }).padBottom(20f).row();
        tTable.table(t -> {
            t.table(tt -> {
                tt.button("画板 " + canvas.emoji(), Styles.cleart, () -> {
                    Cimage = image.copy();
                    create_rbg(palette);
                    canvasGenerator();
                }).size(100, 50);
                tt.add("大小：" + formatNumber(image.width / canvasSize, 0.5f) + "\uE815" + formatNumber(image.height / canvasSize + 1, 0.5f));
            });
            t.row();
            t.table(tt -> {
                tt.button("像素画 " + Blocks.sorter.emoji(), Styles.cleart, () -> {
                    Cimage = image.copy();
                    sorterGenerator();
                }).size(100, 50);
            }).row();
            t.table(tt -> {
                tt.button("逻辑画 " + Blocks.logicDisplay.emoji(), Styles.cleart, () -> {
                    //canvasGenerator();
                }).size(100, 50).disabled(true);
            }).row();
        });
    }

    private float diff_rbg(Integer a, Integer b) {
        int ar = (a >> 16) & 0xFF,
                ag = (a >> 8) & 0xFF,
                ab = a & 0xFF;
        // get in
        int br = (b >> 16) & 0xFF,
                bg = (b >> 8) & 0xFF,
                bb = b & 0xFF;
        int dr = Math.abs(ar - br),
                dg = Math.abs(ag - bg),
                db = Math.abs(ab - bb);
        return dr + dg + db;
    }

    private void create_rbg(int[] colorBar) {
        for (var x = 0; x < image.width; x++) {
            for (var y = 0; y < image.height; y++) {
                Integer pixel = image.get(x, y);

                float egg = 1000;
                for (var other : colorBar) {
                    float h = diff_rbg(pixel, other);
                    if (h < egg) {
                        closest = other;
                        egg = h;
                    }
                }
                Cimage.set(x, y, closest);
            }
        }
    }

    private void canvasGenerator() {
        int width = Cimage.width / canvasSize, height = Cimage.height / canvasSize + 1;
        var tiles = new Seq();
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                // add canvas to the schematic
                CanvasBuild build = (CanvasBuild) canvas.newBuilding();
                // get max 12x12 region of the image
                Pixmap region = Cimage.crop(x * canvasSize, (height - y - 1) * canvasSize, canvasSize, canvasSize);
                // convert pixel data of the region
                byte[] bytes = build.packPixmap(region);
                var stile = new Schematic.Stile(canvas, x * 2, y * 2, bytes, (byte) 0);
                tiles.add(stile);
            }
        }
        StringMap tags = new StringMap();
        tags.put("name", originFile.name());
        var schem = new Schematic(tiles, tags, width * 2, height * 2);
        schem.labels.add(canvas.emoji());
        // Import it
        Vars.schematics.add(schem);
        // Select it
        Vars.ui.schematics.hide();
        Vars.control.input.useSchematic(schem);
        ui.arcInfo("已保存蓝图：" + originFile.name(), 10);
    }

    private void sorterGenerator() {
        var tiles = new Seq();
        for (var y = 0; y < image.height; y++) {
            for (var x = 0; x < image.width; x++) {
                if (image.get(x, y) == 0) continue;
                Sorter.SorterBuild build = (Sorter.SorterBuild) sorter.newBuilding();
                final float[] closestItem = {99999};
                int finalX = x;
                int finalY = y;
                content.items().each(t -> {
                    float dst = diff_rbg(t.color.rgba(), image.get(finalX, finalY));
                    if (dst > closestItem[0]) return;
                    build.sortItem = t;
                    closestItem[0] = dst;
                });
                var stile = new Schematic.Stile(sorter, x, image.height - y - 1, build.config(), (byte) 0);
                tiles.add(stile);
            }
        }
        StringMap tags = new StringMap();
        tags.put("name", originFile.name());
        var schem = new Schematic(tiles, tags, image.width, image.height);
        schem.labels.add(sorter.emoji());
        // Import it
        Vars.schematics.add(schem);
        if (state.isGame()) {
            Vars.ui.schematics.hide();
            Vars.control.input.useSchematic(schem);
        }
        // Select it
        ui.arcInfo("已保存蓝图：" + originFile.name(), 10);
    }

    private float diff_hsv(int[] a, int[] b) {
        float dh = Math.abs(a[0] - b[0]) * 2,
                ds = Math.abs(a[1] - b[1]),
                dv = Math.abs(a[2] - b[2]);
        return dh + ds + dv;
    }

    private void create_hsv() {
        ObjectMap<Integer, int[]> canvasMap = new ObjectMap<>();
        Color tmp = new Color();

        for (int i : palette) {
            canvasMap.put(palette[i], Color.RGBtoHSV(tmp.set(palette[i])));
        }

        for (var x = 0; x < image.width; x++) {
            for (var y = 0; y < image.height; y++) {
                int raw = image.get(x, y);
                int[] pixel = Color.RGBtoHSV(tmp.set(raw));

                float egg = 10000;
                canvasMap.each((a, b) -> {
                    float h = diff_hsv(pixel, b);
                    if (h < egg) {
                        closest = a;
                    }
                });
                Cimage.set(x, y, closest);
            }
        }
    }
}
