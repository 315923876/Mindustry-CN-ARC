package mindustry.entities.abilities;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.arcModule.NumberFormat;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

public class ShieldArcAbility extends Ability{
    private static Unit paramUnit;
    private static ShieldArcAbility paramField;
    private static Vec2 paramPos = new Vec2();
    private static final Cons<Bullet> shieldConsumer = b -> {
        if(b.team != paramUnit.team && b.type.absorbable && paramField.data > 0 &&
            !(b.within(paramPos, paramField.radius - paramField.width/2f) && paramPos.within(b.x - b.deltaX, b.y - b.deltaY, paramField.radius - paramField.width/2f)) &&
            (Tmp.v1.set(b).add(b.deltaX, b.deltaY).within(paramPos, paramField.radius + paramField.width/2f) || b.within(paramPos, paramField.radius + paramField.width/2f)) &&
            (Angles.within(paramPos.angleTo(b), paramUnit.rotation + paramField.angleOffset, paramField.angle / 2f) || Angles.within(paramPos.angleTo(b.x + b.deltaX, b.y + b.deltaY), paramUnit.rotation + paramField.angleOffset, paramField.angle / 2f))){

            b.absorb();
            Fx.absorb.at(b);

            //break shield
            if(paramField.data <= b.damage()){
                paramField.data -= paramField.cooldown * paramField.regen;

                Fx.arcShieldBreak.at(paramPos.x, paramPos.y, 0, paramField.color == null ? paramUnit.type.shieldColor(paramUnit) : paramField.color, paramUnit);
            }

            paramField.data -= b.damage();
            paramField.alpha = 1f;
        }
    };

    /** Shield radius. */
    public float radius = 60f;
    /** Shield regen speed in damage/tick. */
    public float regen = 0.1f;
    /** Maximum shield. */
    public float max = 200f;
    /** Cooldown after the shield is broken, in ticks. */
    public float cooldown = 60f * 5;
    /** Angle of shield arc. */
    public float angle = 80f;
    /** Offset parameters for shield. */
    public float angleOffset = 0f, x = 0f, y = 0f;
    /** If true, only activates when shooting. */
    public boolean whenShooting = true;
    /** Width of shield line. */
    public float width = 6f;

    /** Whether to draw the arc line. */
    public boolean drawArc = true;
    /** If not null, will be drawn on top. */
    public @Nullable String region;
    /** Color override of the shield. Uses unit shield colour by default. */
    public @Nullable Color color;
    /** If true, sprite position will be influenced by x/y. */
    public boolean offsetRegion = false;

    /** State. */
    protected float widthScale, alpha;

    @Override
    public void addStats(Table t){
        super.addStats(t);
        t.add(abilityStat("shield", Strings.autoFixed(max, 2)));
        t.row();
        t.add(abilityStat("repairspeed", Strings.autoFixed(regen * 60f, 2)));
        t.row();
        t.add(abilityStat("cooldown", Strings.autoFixed(cooldown / 60f, 2)));
    }

    @Override
    public void update(Unit unit){

        if(data < max){
            data += Time.delta * regen;
        }

        boolean active = data > 0 && (unit.isShooting || !whenShooting);
        alpha = Math.max(alpha - Time.delta/10f, 0f);

        if(active){
            widthScale = Mathf.lerpDelta(widthScale, 1f, 0.06f);
            paramUnit = unit;
            paramField = this;
            paramPos.set(x, y).rotate(unit.rotation - 90f).add(unit);

            float reach = radius + width / 2f;
            Groups.bullet.intersect(paramPos.x - reach, paramPos.y - reach, reach * 2f, reach * 2f, shieldConsumer);
        }else{
            widthScale = Mathf.lerpDelta(widthScale, 0f, 0.11f);
        }
    }

    @Override
    public void created(Unit unit){
        data = max;
    }

    @Override
    public void draw(Unit unit){
        if(widthScale > 0.001f){
            Draw.z(Layer.shields);

            Draw.color(color == null ? unit.type.shieldColor(unit) : color, Color.white, Mathf.clamp(alpha));
            var pos = paramPos.set(x, y).rotate(unit.rotation - 90f).add(unit);

            if(!Vars.renderer.animateShields){
                Draw.alpha(0.4f);
            }

            if(region != null){
                Vec2 rp = offsetRegion ? pos : Tmp.v1.set(unit);
                Draw.yscl = widthScale;
                Draw.rect(region, rp.x, rp.y, unit.rotation - 90);
                Draw.yscl = 1f;
            }

            if(drawArc){
                Lines.stroke(width * widthScale);
                Lines.arc(pos.x, pos.y, radius, angle / 360f, unit.rotation + angleOffset - angle / 2f);
            }
            Draw.reset();
        }
    }

    @Override
    public void displayBars(Unit unit, Table bars){
        bars.add(new Bar(() -> NumberFormat.formatPercent((data < 0? "[red]":"") + "\uE84D", data, max), () -> Pal.accent, () -> data / max)).row();
    }
}
