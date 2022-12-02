package mindustry.arcModule.toolpack;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.core.UI;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.liquid.LiquidBridge;
import mindustry.world.blocks.liquid.LiquidJunction;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.BlockGroup;


import static mindustry.Vars.*;
import static mindustry.arcModule.RFuncs.calWaveTimer;
import static mindustry.arcModule.toolpack.arcWaveSpawner.*;

public class arcScanMode {

    private static Table st = new Table(Styles.black3);

    private static Table ct = new Table(Styles.none);
    private static Table ctTable = new Table();
    /** spawner */
    private static Table spt, sfpt;
    private static Table spawnerTable = new Table();
    private static Table flyerTable = new Table();
    static float thisAmount, thisHealth, thisEffHealth, thisDps;
    static int totalAmount = 0, totalHealth = 0, totalEffHealth = 0, totalDps = 0;

    static int tableCount = 0;
    /**
     * conveyor
     */
    static final int maxLoop = 200;
    static int forwardIndex = 0;
    static Seq<Building> forwardBuild = new Seq<>();


    static {
        {
            st.touchable = Touchable.disabled;
            st.margin(8f).add(">> 扫描详情模式 <<").color(getThemeColor()).style(Styles.outlineLabel).labelAlign(Align.center);
            st.visible = true;
            st.update(() -> st.setPosition(Core.graphics.getWidth() / 2f, Core.graphics.getHeight() * 0.7f, Align.center));
            st.pack();
            st.update(() -> st.visible = control.input.arcScanMode && state.isPlaying());
            Core.scene.add(st);
        }
        {
            ct.touchable = Touchable.disabled;
            ct.visible = false;
            ct.add(ctTable).margin(8f);
            ct.pack();
            ct.update(() -> ct.visible = ct.visible && state.isPlaying());
            Core.scene.add(ct);
        }
        {
            spt = new Table();
            spt.touchable = Touchable.disabled;
            spt.visible = false;
            spt.add(spawnerTable).margin(8f);
            spt.pack();
            spt.update(() -> spt.visible = spt.visible && state.isPlaying());
            Core.scene.add(spt);

            sfpt = new Table();
            sfpt.touchable = Touchable.disabled;
            sfpt.visible = false;
            sfpt.add(flyerTable).margin(8f);
            sfpt.pack();
            sfpt.update(() -> sfpt.visible = sfpt.visible && state.isPlaying());
            Core.scene.add(sfpt);
        }
    }

    public static void arcScan() {
        detailCursor();
        detailSpawner();
        detailTransporter();
    }

    private static void detailCursor() {
        ct.visible = ct.visible && state.isPlaying();
        ctTable.clear();
        if (!control.input.arcScanMode) {
            ct.visible = false;
            return;
        }
        ct.setPosition(Core.input.mouseX(), Core.input.mouseY());
        ct.visible = true;
        ctTable.table(ctt -> {
            ctt.add((int) (Core.input.mouseWorldX() / 8) + "," + (int) (Core.input.mouseWorldY() / 8));
            ctt.row();
            ctt.add("距离：" + (int) (Mathf.dst(player.x, player.y, Core.input.mouseWorldX(), Core.input.mouseWorldY()) / 8));
        });
    }

    private static void detailSpawner() {
        spt.visible = spt.visible && state.isPlaying();
        sfpt.visible = sfpt.visible && state.isPlaying();
        spawnerTable.clear();
        flyerTable.clear();
        if (!control.input.arcScanMode) {
            spt.visible = false;
            sfpt.visible = false;
            return;
        }
        totalAmount = 0;
        totalHealth = 0;
        totalEffHealth = 0;
        totalDps = 0;
        int curInfoWave = state.wave + 1;
        for (Tile tile : spawner.getSpawns()) {
            if (Mathf.dst(tile.worldx(), tile.worldy(), Core.input.mouseWorldX(), Core.input.mouseWorldY()) < state.rules.dropZoneRadius) {
                float curve = Mathf.curve(Time.time % 240f, 120f, 240f);
                Draw.z(Layer.effect - 2f);
                Draw.color(state.rules.waveTeam.color);
                Lines.stroke(4f);
                //flyer
                float flyerAngle = Angles.angle(world.width() / 2f, world.height() / 2f, tile.x, tile.y);
                float trns = Math.max(world.width(), world.height()) * Mathf.sqrt2 * tilesize;
                float spawnX = Mathf.clamp(world.width() * tilesize / 2f + Angles.trnsx(flyerAngle, trns), 0, world.width() * tilesize);
                float spawnY = Mathf.clamp(world.height() * tilesize / 2f + Angles.trnsy(flyerAngle, trns), 0, world.height() * tilesize);
                if (hasFlyer) {
                    Lines.line(tile.worldx(), tile.worldy(), spawnX, spawnY);
                    Tmp.v1.set(spawnX - tile.worldx(), spawnY - tile.worldy());
                    Tmp.v1.setLength(Tmp.v1.len() * curve);
                    Fill.circle(tile.worldx() + Tmp.v1.x, tile.worldy() + Tmp.v1.y, 8f);

                    Vec2 v = Core.camera.project(spawnX, spawnY);
                    sfpt.setPosition(v.x, v.y);
                    sfpt.visible = true;

                    flyerTable.table(Styles.black3, tt -> {
                        tt.add(calWaveTimer()).row();
                        state.rules.spawns.each(group -> group.type.flying && group.spawn == -1 || (tile.x == Point2.x(group.spawn) && tile.y == Point2.y(group.spawn)), group -> {
                            thisAmount = group.getSpawned(curInfoWave);
                            if (thisAmount > 0) {
                                thisHealth = (group.type.health + group.getShield(curInfoWave)) * thisAmount;
                                if (group.effect == null) {
                                    thisEffHealth = (group.type.health + group.getShield(curInfoWave)) * thisAmount;
                                    thisDps = group.type.estimateDps();
                                } else {
                                    thisEffHealth = group.effect.healthMultiplier * (group.type.health + group.getShield(curInfoWave)) * thisAmount;
                                    thisDps = group.effect.damageMultiplier * group.effect.reloadMultiplier * group.type.estimateDps();
                                }
                                totalAmount += thisAmount;
                                totalHealth += thisHealth;
                                totalEffHealth += thisEffHealth;
                                totalDps += thisDps;
                            }
                        });
                        if (totalAmount == 0) tt.add("该波次没有敌人");
                        else {
                            tt.table(wi -> {
                                wi.add("\uE86D").width(50f);
                                wi.add("[accent]" + totalAmount).growX().padRight(50f);
                                wi.add("\uE813").width(50f);
                                wi.add("[accent]" + UI.formatAmount(totalHealth, 2)).growX().padRight(50f);
                                if (totalEffHealth != totalHealth) {
                                    wi.add("\uE810").width(50f);
                                    wi.add("[accent]" + UI.formatAmount(totalEffHealth, 2)).growX().padRight(50f);
                                }
                                wi.add("\uE86E").width(50f);
                                wi.add("[accent]" + UI.formatAmount(totalDps, 2)).growX();
                            });
                        }
                        tt.row();
                        tableCount = 0;
                        tt.table(wi -> state.rules.spawns.each(group -> group.type.flying && group.spawn == -1 || (tile.x == Point2.x(group.spawn) && tile.y == Point2.y(group.spawn)), group -> {
                            int amount = group.getSpawned(curInfoWave);
                            if (amount > 0) {
                                tableCount += 1;
                                if (tableCount % 10 == 0) wi.row();

                                StringBuilder groupInfo = new StringBuilder();
                                groupInfo.append(group.type.emoji());

                                groupInfo.append(group.type.typeColor());

                                groupInfo.append("\n").append(amount);
                                groupInfo.append("\n");

                                if (group.getShield(curInfoWave) > 0f)
                                    groupInfo.append(UI.formatAmount((long) group.getShield(curInfoWave)));
                                groupInfo.append("\n[]");
                                if (group.effect != null && group.effect != StatusEffects.none)
                                    groupInfo.append(group.effect.emoji());
                                if (group.items != null && group.items.amount > 0)
                                    groupInfo.append(group.items.item.emoji());
                                if (group.payloads != null && group.payloads.size > 0)
                                    groupInfo.append("\uE87B");
                                wi.add(groupInfo.toString()).height(130f).width(50f);
                            }
                        })).scrollX(true).scrollY(false).maxWidth(mobile ? 400f : 750f).growX();
                    });
                }
                //ground
                totalAmount = 0;
                totalHealth = 0;
                totalEffHealth = 0;
                totalDps = 0;

                if (curve > 0)
                    Lines.circle(tile.worldx(), tile.worldy(), state.rules.dropZoneRadius * Interp.pow3Out.apply(curve));
                Lines.circle(tile.worldx(), tile.worldy(), state.rules.dropZoneRadius);
                Lines.arc(tile.worldx(), tile.worldy(), state.rules.dropZoneRadius - 3f, state.wavetime / state.rules.waveSpacing, 90f);
                float angle = Mathf.pi / 2 + state.wavetime / state.rules.waveSpacing * 2 * Mathf.pi;
                Draw.color(state.rules.waveTeam.color);
                Fill.circle(tile.worldx() + state.rules.dropZoneRadius * Mathf.cos(angle), tile.worldy() + state.rules.dropZoneRadius * Mathf.sin(angle), 8f);

                Vec2 v = Core.camera.project(tile.worldx(), tile.worldy());
                spt.setPosition(v.x, v.y);
                spt.visible = true;
                spawnerTable.table(Styles.black3, tt -> {
                    tt.add(calWaveTimer()).row();
                    state.rules.spawns.each(group -> !group.type.flying && group.spawn == -1 || (tile.x == Point2.x(group.spawn) && tile.y == Point2.y(group.spawn)), group -> {
                        thisAmount = group.getSpawned(curInfoWave);
                        if (thisAmount > 0) {
                            thisHealth = (group.type.health + group.getShield(curInfoWave)) * thisAmount;
                            if (group.effect == null) {
                                thisEffHealth = (group.type.health + group.getShield(curInfoWave)) * thisAmount;
                                thisDps = group.type.estimateDps();
                            } else {
                                thisEffHealth = group.effect.healthMultiplier * (group.type.health + group.getShield(curInfoWave)) * thisAmount;
                                thisDps = group.effect.damageMultiplier * group.effect.reloadMultiplier * group.type.estimateDps();
                            }
                            totalAmount += thisAmount;
                            totalHealth += thisHealth;
                            totalEffHealth += thisEffHealth;
                            totalDps += thisDps;
                        }
                    });
                    if (totalAmount == 0) tt.add("该波次没有敌人");
                    else {
                        tt.table(wi -> {
                            wi.add("\uE86D").width(50f);
                            wi.add("[accent]" + totalAmount).growX().padRight(50f);
                            wi.add("\uE813").width(50f);
                            wi.add("[accent]" + UI.formatAmount(totalHealth, 2)).growX().padRight(50f);
                            if (totalEffHealth != totalHealth) {
                                wi.add("\uE810").width(50f);
                                wi.add("[accent]" + UI.formatAmount(totalEffHealth, 2)).growX().padRight(50f);
                            }
                            wi.add("\uE86E").width(50f);
                            wi.add("[accent]" + UI.formatAmount(totalDps, 2)).growX();
                        });
                    }
                    tt.row();
                    tableCount = 0;
                    tt.table(wi -> state.rules.spawns.each(group -> !group.type.flying && group.spawn == -1 || (tile.x == Point2.x(group.spawn) && tile.y == Point2.y(group.spawn)), group -> {
                        int amount = group.getSpawned(curInfoWave);
                        if (amount > 0) {
                            tableCount += 1;
                            if (tableCount % 10 == 0) wi.row();

                            StringBuilder groupInfo = new StringBuilder();
                            groupInfo.append(group.type.emoji());

                            groupInfo.append(group.type.typeColor());

                            groupInfo.append("\n").append(amount);
                            groupInfo.append("\n");

                            if (group.getShield(curInfoWave) > 0f)
                                groupInfo.append(UI.formatAmount((long) group.getShield(curInfoWave)));
                            groupInfo.append("\n[]");
                            if (group.effect != null && group.effect != StatusEffects.none)
                                groupInfo.append(group.effect.emoji());
                            if (group.items != null && group.items.amount > 0)
                                groupInfo.append(group.items.item.emoji());
                            if (group.payloads != null && group.payloads.size > 0)
                                groupInfo.append("\uE87B");
                            wi.add(groupInfo.toString()).height(130f).width(50f);
                        }
                    })).scrollX(true).scrollY(false).maxWidth(mobile ? 400f : 750f).growX();
                });
                return;
            }
        }

        spt.visible = false;
        spawnerTable.clear();
    }

    private static void detailTransporter() {
        if (!control.input.arcScanMode) return;

        //check tile being hovered over
        Tile hoverTile = world.tileWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
        if (hoverTile == null || hoverTile.build == null || !hoverTile.build.displayable() || hoverTile.build.inFogTo(player.team())) {
            return;
        }
        forwardIndex = 0; forwardBuild.clear();

        forward(hoverTile.build, hoverTile.build);
        //drawBuild();
    }

    private static void forward(Building cur, Building last) {
        forward(cur, last, 0);
    }

    private static void forward(Building cur, Building last, int conduit) {
        /** 检查cur并添加到forwardBuild, 同时迭代下一个的循环 */

        //能否接受
        if (forwardIndex == maxLoop || cur == null || conduit == 3) return;
        if (last.team != cur.team || (cur.team != player.team() && cur.inFogTo(player.team()))) return;
        if (cur.block.itemCapacity == 0 && !cur.block.instantTransfer) return;
        if (cur instanceof LiquidJunction.LiquidJunctionBuild) return;

        //接受成功
        forwardIndex += 1;
        //绘制
        Draw.color(Color.gold);
        Lines.stroke(1.5f);
        float dst = Mathf.dst(last.tile.worldx(), last.tile.worldy(), cur.tile.worldx(), cur.tile.worldy());
        Lines.line(last.tile.worldx(), last.tile.worldy(), cur.tile.worldx(), cur.tile.worldy());
        Fill.circle(cur.tile.worldx(), cur.tile.worldy(), 2f);
        if (dst > 8f) {
            Draw.color(Color.orange);
            Drawf.simpleArrow(last.tile.worldx(), last.tile.worldy(), cur.tile.worldx(), cur.tile.worldy(), dst / 2, 4f);
        }

        if ((cur.block.group != BlockGroup.transportation && canAccept(cur.block)))
            Drawf.selected(cur, Tmp.c1.set(Color.red).a(Mathf.absin(4f, 1f) * 0.5f + 0.5f));

        //准备下一迭代
        if (!forwardBuild.addUnique(cur)) return; //循环直接卡出，导致寻路不准，但问题应该不大
        //if (cur.block.group != BlockGroup.transportation) return;

        //处理超传
        conduit = cur.block.instantTransfer ? conduit + 1 : 0;

        //默认的下一个
        int from = cur.relativeToEdge(last.tile);

        if (cur instanceof CoreBlock.CoreBuild || cur instanceof StorageBlock.StorageBuild) {
            Drawf.selected(cur, Tmp.c1.set(Color.red).a(Mathf.absin(4f, 1f) * 0.5f + 0.5f));
        } else if (cur instanceof OverflowGate.OverflowGateBuild || cur instanceof Router.RouterBuild
                || cur instanceof DuctRouter.DuctRouterBuild) { // 三向任意口，留意暂不处理路由器回流，即便这是可行的
            dumpForward(cur, last, conduit);
        } else if (cur instanceof Sorter.SorterBuild sb) {  // 分类的特殊情况
            if (sb.sortItem != null || !((Sorter) sb.block).invert) forward(cur.nearby((from + 1) % 4), cur, conduit);
            if (sb.sortItem != null || ((Sorter) sb.block).invert) forward(cur.nearby((from + 2) % 4), cur, conduit);
            if (sb.sortItem != null || !((Sorter) sb.block).invert) forward(cur.nearby((from + 3) % 4), cur, conduit);
        } else if (cur instanceof DuctBridge.DuctBridgeBuild ductBridgeBuild) {
            if (ductBridgeBuild.arcLinkValid()) forward(ductBridgeBuild.findLink(), cur);
            else forward(cur.front(), cur);
        } else if (cur instanceof ItemBridge.ItemBridgeBuild itemBridgeBuild && ! (cur instanceof LiquidBridge.LiquidBridgeBuild)) {
            if (world.tile(itemBridgeBuild.link) != null) forward(world.tile(itemBridgeBuild.link).build, cur);
            else if (itemBridgeBuild.incoming.size == 0) return;
            else dumpNearby(cur, last, conduit);
        } else if (cur instanceof MassDriver.MassDriverBuild massDriverBuild && massDriverBuild.arcLinkValid()) {
            forward(world.tile(massDriverBuild.link).build, cur);
            if (massDriverBuild.state == MassDriver.DriverState.idle || massDriverBuild.state == MassDriver.DriverState.accepting)
                dumpNearby(cur, last, conduit);
        } else if (cur instanceof Conveyor.ConveyorBuild || cur instanceof Duct.DuctBuild || cur instanceof StackConveyor.StackConveyorBuild) {
            forward(cur.front(), cur);
            if (cur instanceof StackConveyor.StackConveyorBuild stackConveyorBuild && stackConveyorBuild.state == 2)
                dumpForward(cur, last, conduit);
        } else if (cur instanceof Junction.JunctionBuild) {
            forward(cur.nearby((from + 2) % 4), cur, conduit);
        } else {
            return;
        }
    }

    private static void dumpForward(Building cur, Building last, int conduit) {
        int from = cur.relativeToEdge(last.tile);
        forward(cur.nearby((from + 1) % 4), cur, conduit);
        forward(cur.nearby((from + 2) % 4), cur, conduit);
        forward(cur.nearby((from + 3) % 4), cur, conduit);
    }

    private static void dumpNearby(Building cur, Building last, int conduit) {
        cur.proximity.each(building -> {
            if (cur.canDump(building, Items.copper) && (building != null && canAccept(building.block)))
                forward(building, cur, conduit);
        });
    }

    private static void drawBuild() {
        forwardBuild.each(building -> Drawf.selected(building, Tmp.c1.set(Color.red).a(Mathf.absin(4f, 1f) * 0.5f + 0.5f)));

        forwardIndex = 0;
        forwardBuild.clear();
    }

    private static boolean canAccept(Block block) {
        if (block.group == BlockGroup.transportation) return true;
        for (Item item : content.items()) {
            if (block.consumesItem(item) || block.itemCapacity > 0) {
                return true;
            }
        }
        return false;
    }

}