package mindustry.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.arcModule.Marker;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.ui.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class MinimapRenderer{
    private static final float baseSize = 16f, updateInterval = 2f;

    private final Seq<Unit> units = new Seq<>();
    private Pixmap pixmap;
    private Texture texture;
    private TextureRegion region;
    private Rect rect = new Rect();
    private float zoom = 4;

    private float lastX, lastY, lastW, lastH, lastScl;
    private boolean worldSpace;
    private IntSet updates = new IntSet();
    private float updateCounter = 0f;

    public MinimapRenderer(){
        Events.on(WorldLoadEvent.class, event -> {
            reset();
            updateAll();
        });

        Events.on(TileChangeEvent.class, event -> {
            if(!ui.editor.isShown()){
                update(event.tile);

                //update floor below block.
                if(event.tile.block().solid && event.tile.y > 0 && event.tile.isCenter()){
                    event.tile.getLinkedTiles(t -> {
                        Tile tile = world.tile(t.x, t.y - 1);
                        if(tile != null && tile.block() == Blocks.air){
                            update(tile);
                        }
                    });
                }
            }
        });

        Events.on(TilePreChangeEvent.class, e -> {
            //update floor below a *recently removed* block.
            if(e.tile.block().solid && e.tile.y > 0){
                Tile tile = world.tile(e.tile.x, e.tile.y - 1);
                if(tile.block() == Blocks.air){
                    Core.app.post(() -> update(tile));
                }
            }
        });

        Events.on(BuildTeamChangeEvent.class, event -> update(event.build.tile));
    }

    public void update(){
        //updates are batched to occur every 2 frames
        if((updateCounter += Time.delta) >= updateInterval){
            updateCounter %= updateInterval;

            updates.each(pos -> {
                Tile tile = world.tile(pos);
                if(tile == null) return;

                int color = colorFor(tile);
                pixmap.set(tile.x, pixmap.height - 1 - tile.y, color);

                //yes, this calls glTexSubImage2D every time, with a 1x1 region
                Pixmaps.drawPixel(texture, tile.x, pixmap.height - 1 - tile.y, color);
            });

            updates.clear();
        }
    }

    public Pixmap getPixmap(){
        return pixmap;
    }

    public @Nullable Texture getTexture(){
        return texture;
    }

    public void zoomBy(float amount){
        zoom += amount;
        setZoom(zoom);
    }

    public void setZoom(float amount){
        zoom = Mathf.clamp(amount, 1f, Math.max(world.width(), world.height()) / baseSize / 2f);
    }

    public float getZoom(){
        return zoom;
    }

    public void reset(){
        updates.clear();
        if(pixmap != null){
            pixmap.dispose();
            texture.dispose();
        }
        setZoom(4f);
        pixmap = new Pixmap(world.width(), world.height());
        texture = new Texture(pixmap);
        region = new TextureRegion(texture);
    }

    public void drawEntities(float x, float y, float w, float h, boolean fullView){

        if(!fullView){
            updateUnitArray();
        }else{
            units.clear();
            Groups.unit.copy(units);
        }

        float sz = baseSize * zoom;
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, world.width() - sz);
        dy = Mathf.clamp(dy, sz, world.height() - sz);

        rect.set((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2 * tilesize, sz * 2 * tilesize);

        float Rwidth = !fullView ? Core.camera.width / rect.width * w : Core.camera.width / (world.width() * tilesize) * w;
        float Rheight = !fullView ? Core.camera.height / rect.width * h : Core.camera.height / (world.height() * tilesize) * h;
        Lines.rect(x + transfromX(fullView,w,Core.camera.position.x) - Rwidth/2,y + transfromY(fullView,h,Core.camera.position.y) - Rheight/2,Rwidth, Rheight );

        if(Marker.markList.size>0) {
            Marker.markList.each(a->{
                if((Time.time - a.time) > Marker.retainTime) return;
                Draw.color(a.markType.color);
                Lines.stroke(Scl.scl(3f) * (1 - (Time.time % 180 + 30) / 210));

                float rx = transfromX(fullView,w,a.markPos.x);
                float ry = transfromY(fullView,h,a.markPos.y);

                Lines.circle(x + rx, y + ry, scale(100f) * (Time.time % 180) / 180);
                Lines.stroke(Scl.scl(3f));
                Lines.circle(x + rx, y + ry, scale(20f));
                Lines.arc(x + rx, y + ry, scale(18f),1 - (Time.time - a.time)/Marker.retainTime);
                Draw.reset();
            });
        }

        Tmp.m2.set(Draw.trans());

        float scaleFactor;
        var trans = Tmp.m1.idt();
        trans.translate(x, y);
        if(!fullView){
            trans.scl(Tmp.v1.set(scaleFactor = w / rect.width, h / rect.height));
            trans.translate(-rect.x, -rect.y);
        }else{
            trans.scl(Tmp.v1.set(scaleFactor = w / world.unitWidth(), h / world.unitHeight()));
        }
        trans.translate(tilesize / 2f, tilesize / 2f);
        Draw.trans(trans);

        scaleFactor = 1f / scaleFactor;

        if(unitDetailsIcon){
            for(Unit unit : units){
                if(unit.inFogTo(player.team()) || !unit.type.drawMinimap) continue;

                float rx = !fullView ? (unit.x - rect.x) / rect.width * w : unit.x / (world.width() * tilesize) * w;
                float ry = !fullView ? (unit.y - rect.y) / rect.width * h : unit.y / (world.height() * tilesize) * h;

                float scale = Scl.scl(1f) / 2f * scaling * 32f * unit.hitSize / tilesize / 2;
                var region = unit.icon();
                Draw.rect(region, x + rx, y + ry, scale, scale * (float) region.height / region.width, unit.rotation() - 90);
                Draw.reset();
                Draw.mixcol(unit.team.color, 0.3f);
                Draw.rect(region, x + rx, y + ry, scale, scale * (float) region.height / region.width, unit.rotation() - 90);
                Draw.reset();
            }
        } else {
            for (Unit unit : units) {
                if (unit.inFogTo(player.team()) || !unit.type.drawMinimap) continue;

                float rx = !fullView ? (unit.x - rect.x) / rect.width * w : unit.x / (world.width() * tilesize) * w;
                float ry = !fullView ? (unit.y - rect.y) / rect.width * h : unit.y / (world.height() * tilesize) * h;

                Draw.mixcol(unit.team.color, 1f);
                float scale = Scl.scl(1f) / 2f * scaling * 32f;
                var region = unit.icon();
                Draw.rect(region, x + rx, y + ry, scale, scale * (float)region.height / region.width, unit.rotation() - 90);
                Draw.reset();
            }
        }

        if(net.active() && (fullView || forceShowPlayer)){
            for(Player player : Groups.player){
                if(!player.dead()){
                    drawLabel(player.x, player.y, player.name, player.color);
                    float rx = !fullView ? (player.x - rect.x) / rect.width * w : player.x / (world.width() * tilesize) * w;
                    float ry = !fullView ? (player.y - rect.y) / rect.width * h : player.y / (world.height() * tilesize) * h;

                    drawLabel(x + rx, y + ry, player.name, player.color);
                }
            }
        }

        Draw.reset();

        if(state.rules.fog){
            if(fullView){
                float z = zoom;
                //max zoom out fixes everything, somehow?
                setZoom(99999f);
                getRegion();
                zoom = z;
            }
            Draw.shader(Shaders.fog);
            Texture staticTex = renderer.fog.getStaticTexture(), dynamicTex = renderer.fog.getDynamicTexture();

            //crisp pixels
            dynamicTex.setFilter(TextureFilter.nearest);

            Tmp.tr1.set(dynamicTex);
            Tmp.tr1.set(0f, 1f, 1f, 0f);

            float wf = world.width() * tilesize;
            float hf = world.height() * tilesize;

            Draw.color(state.rules.dynamicColor, 0.5f);
            Draw.rect(Tmp.tr1, wf / 2, hf / 2, wf, hf);

            if(state.rules.staticFog){
                staticTex.setFilter(TextureFilter.nearest);

                Tmp.tr1.texture = staticTex;
                //must be black to fit with borders
                Draw.color(0f, 0f, 0f, 1f);
                Draw.rect(Tmp.tr1, wf / 2, hf / 2, wf, hf);
            }

            Draw.color();
            Draw.shader();
        }

        //TODO might be useful in the standard minimap too
        if(fullView){
            drawSpawns(x, y, w, h, scaling);

            if(!mobile){
                //draw bounds for camera - not drawn on mobile because you can't shift it by tapping anyway
                Rect r = Core.camera.bounds(Tmp.r1);
                Lines.stroke(Scl.scl(3f) * scaleFactor);
                Draw.color(Pal.accent);
                Lines.rect(r.x, r.y, r.width, r.height);
                Draw.reset();
            }
        }

        LongSeq indicators = control.indicators.list();
        float fin = ((Time.globalTime / 30f) % 1f);
        float rad = fin * 5f + tilesize - 2f;
        Lines.stroke(Scl.scl((1f - fin) * 4f + 0.5f));

        for(int i = 0; i < indicators.size; i++){
            long ind = indicators.items[i];
            int
                pos = Indicator.pos(ind),
                ix = Point2.x(pos),
                iy = Point2.y(pos);
            float time = Indicator.time(ind), offset = 0f;

            //fix multiblock offset - this is suboptimal
            Building build = world.build(pos);
            if(build != null){
                offset = build.block.offset / tilesize;
            }

            Draw.color(Color.orange, Color.scarlet, Mathf.clamp(time / 70f));

            Lines.square((ix + 0.5f + offset) * tilesize, (iy + 0.5f + offset) * tilesize, rad);
        }

        Draw.reset();

        //TODO autoscale markers
        state.rules.objectives.eachRunning(obj -> {
            for(var marker : obj.markers){
                if(marker.minimap){
                    marker.draw(1);
                }
            }
        });

        for(var marker : state.markers){
            if(marker.minimap){
                marker.draw(1);
            }
        }

        Draw.trans(Tmp.m2);
    }


        private float transfromX(boolean withLabels,float w,float x){
        return !withLabels ? (x - rect.x) / rect.width * w : x / (world.width() * tilesize) * w;
    }
    private float transfromY(boolean withLabels,float h,float y){
        return !withLabels ? (y - rect.y) / rect.width * h : y / (world.height() * tilesize) * h;
    }

    public void drawSpawns(float x, float y, float w, float h, float scaling){
        if(!(state.rules.showSpawns || Core.settings.getBool("alwaysshowdropzone")) || !state.hasSpawns() || !state.rules.waves) return;

        TextureRegion icon = Icon.units.getRegion();

        Lines.stroke(Scl.scl(3f));

        Draw.color(state.rules.waveTeam.color, Tmp.c2.set(state.rules.waveTeam.color).value(1.2f), Mathf.absin(Time.time, 16f, 1f));

        float rad = scale(state.rules.dropZoneRadius);
        float curve = Mathf.curve(Time.time % 240f, 120f, 240f);

        for(Tile tile : spawner.getSpawns()){
            float tx = ((tile.x + 0.5f) / world.width()) * w;
            float ty = ((tile.y + 0.5f) / world.height()) * h;

            Draw.rect(icon, x + tx, y + ty, icon.width, icon.height);
            Lines.circle(x + tx, y + ty, rad);
            if(curve > 0f) Lines.circle(x + tx, y + ty, rad * Interp.pow3Out.apply(curve));
        }

        Draw.reset();
    }

    public float scale(float radius){
        return worldSpace ? (radius / (baseSize / 2f)) * 5f * lastScl : lastW / rect.width * radius;
    }

    public @Nullable TextureRegion getRegion(){
        if(texture == null) return null;

        float sz = Mathf.clamp(baseSize * zoom, baseSize, Math.max(world.width(), world.height()));
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, world.width() - sz);
        dy = Mathf.clamp(dy, sz, world.height() - sz);
        float invTexWidth = 1f / texture.width;
        float invTexHeight = 1f / texture.height;
        float x = dx - sz, y = world.height() - dy - sz, width = sz * 2, height = sz * 2;
        region.set(x * invTexWidth, y * invTexHeight, (x + width) * invTexWidth, (y + height) * invTexHeight);
        return region;
    }

    public void updateAll(){
        for(Tile tile : world.tiles){
            pixmap.set(tile.x, pixmap.height - 1 - tile.y, colorFor(tile));
        }
        texture.draw(pixmap);
    }

    public void update(Tile tile){
        if(world.isGenerating() || !state.isGame()) return;

        if(tile.build != null && tile.isCenter()){
            tile.getLinkedTiles(other -> {
                if(!other.isCenter()){
                    updatePixel(other);
                }

                if(tile.block().solid && other.y > 0){
                    Tile low = world.tile(other.x, other.y - 1);
                    if(!low.solid()){
                        updatePixel(low);
                    }
                }
            });
        }

        updatePixel(tile);
    }

    void updatePixel(Tile tile){
        updates.add(tile.pos());
    }

    public void updateUnitArray(){
        float sz = baseSize * zoom;
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, world.width() - sz);
        dy = Mathf.clamp(dy, sz, world.height() - sz);

        units.clear();
        Units.nearby((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2 * tilesize, sz * 2 * tilesize, units::add);
    }

    private Block realBlock(Tile tile){
        //TODO doesn't work properly until player goes and looks at block
        return tile.build == null ? tile.block() : state.rules.fog && !tile.build.wasVisible ? Blocks.air : tile.block();
    }

    private int colorFor(Tile tile){
        if(tile == null) return 0;
        Block real = realBlock(tile);
        int bc = real.minimapColor(tile);

        Color color = Tmp.c1.set(bc == 0 ? MapIO.colorFor(real, tile.floor(), tile.overlay(), tile.team()) : bc);
        color.mul(1f - Mathf.clamp(world.getDarkness(tile.x, tile.y) / 4f));

        if(real == Blocks.air && tile.y < world.height() - 1 && realBlock(world.tile(tile.x, tile.y + 1)).solid){
            color.mul(0.7f);
        }else if(tile.floor().isLiquid && (tile.y >= world.height() - 1 || !world.tile(tile.x, tile.y + 1).floor().isLiquid)){
            color.mul(0.84f, 0.84f, 0.9f, 1f);
        }

        return color.rgba();
    }

    public void drawLabel(float x, float y, String text, Color color){
        Font font = Fonts.outline;
        GlyphLayout l = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        font.getData().setScale(1 / 1.5f / Scl.scl(1f));
        font.setUseIntegerPositions(false);

        l.setText(font, text, color, 90f, Align.left, true);
        float yOffset = 20f;
        float margin = 3f;

        Draw.color(0f, 0f, 0f, 0.2f);
        Fill.rect(x, y + yOffset - l.height/2f, l.width + margin, l.height + margin);
        Draw.color();
        font.setColor(color);
        font.draw(text, x - l.width/2f, y + yOffset, 90f, Align.left, true);
        font.setUseIntegerPositions(ints);

        font.getData().setScale(1f);

        Pools.free(l);
    }
}
