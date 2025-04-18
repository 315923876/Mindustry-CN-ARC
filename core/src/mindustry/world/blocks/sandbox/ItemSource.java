package mindustry.world.blocks.sandbox;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.meta.*;
import arc.*;

import static mindustry.Vars.*;

public class ItemSource extends Block{
    public int itemsPerSecond = 100;

    public ItemSource(String name){
        super(name);
        hasItems = true;
        update = true;
        solid = true;
        group = BlockGroup.transportation;
        configurable = true;
        saveConfig = true;
        noUpdateDisabled = true;
        envEnabled = Env.any;
        clearOnDoubleTap = true;

        config(Item.class, (ItemSourceBuild tile, Item item) -> tile.outputItem = item);
        configClear((ItemSourceBuild tile) -> tile.outputItem = null);
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("items");
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(Stat.output, itemsPerSecond, StatUnit.itemsSecond);
    }

    @Override
    protected TextureRegion[] icons(){
        return new TextureRegion[]{Core.atlas.find("source-bottom"), region};
    }

    @Override
    public void drawPlanConfig(BuildPlan plan, Eachable<BuildPlan> list){
        drawPlanConfigCenter(plan, plan.config, "center", true);
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    public class ItemSourceBuild extends Building{
        public float counter;
        public Item outputItem;

        @Override
        public void draw(){
            if(outputItem == null){
                Draw.rect("cross-full", x, y);
            }else{
                Draw.color(outputItem.color);
                Fill.square(x, y, tilesize/2f - 0.00001f);
                Draw.color();
                if(Core.settings.getBool("arcchoiceuiIcon"))    Draw.rect(outputItem.uiIcon, x, y,4f,4f);
            }

            super.draw();
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            drawItemSelection(outputItem);
        }

        @Override
        public void updateTile(){
            if(outputItem == null) return;

            counter += edelta();
            float limit = 60f / itemsPerSecond;

            while(counter >= limit){
                items.set(outputItem, 1);
                dump(outputItem);
                produced(outputItem);
                items.set(outputItem, 0);
                counter -= limit;
            }
        }

        @Override
        public void buildConfiguration(Table table){
            ItemSelection.buildTable(ItemSource.this, table, content.items(), () -> outputItem, this::configure, selectionRows, selectionColumns);
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return false;
        }

        @Override
        public Item config(){
            return outputItem;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.s(outputItem == null ? -1 : outputItem.id);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            outputItem = content.item(read.s());
        }
    }
}
