package io.github.wickidcow.probe;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mmmjjkx.betterChests.items.chests.SimpleDrawer;
import me.mmmjjkx.betterChests.items.chests.ie.IEStorageUnit;
import me.mmmjjkx.betterChests.storage.DrawerData;
import me.mmmjjkx.betterChests.storage.DrawerStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Disposable actual-server fixture; no fake Player and no production data. */
public final class BetterChestsProbe extends JavaPlugin {
    private final String phase = System.getProperty("probe.phase", "invalid");
    private static final NamespacedKey OWNER = new NamespacedKey("preservation_probe", "owner");
    private static final NamespacedKey LONG = new NamespacedKey("preservation_probe", "old_long");
    private static final NamespacedKey FLOAT = new NamespacedKey("preservation_probe", "old_float");
    private int assertions;
    private void require(boolean value, String why) {
        assertions++;
        if (!value) throw new AssertionError(why);
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("PROBE_PASS " + phase + " assertions=" + assertions); }
            catch (Throwable failure) { getLogger().log(java.util.logging.Level.SEVERE, "PROBE_FAIL " + phase, failure); }
        }, 80L);
    }
    private Block at(World world,int x) { return world.getBlockAt(x,80,8); }
    private void create(Block block,String id) {
        SlimefunItem item=SlimefunItem.getById(id);
        require(item!=null,"registered id " + id);
        block.setType(item.getItem().getType(),false);
        Slimefun.getDatabaseManager().getBlockDataController().createBlock(block.getLocation(),id);
    }
    private ItemStack richItem() {
        ItemStack item=new ItemStack(Material.DIAMOND,1);
        var m=item.getItemMeta();
        m.displayName(Component.text("Historical owner item"));
        m.lore(List.of(Component.text("Preserve this exact lore"),Component.empty(),Component.text("Old item")));
        var p=m.getPersistentDataContainer();
        p.set(OWNER,PersistentDataType.STRING,"fixed-owner-47");
        p.set(LONG,PersistentDataType.LONG,9007199254740993L);
        p.set(FLOAT,PersistentDataType.FLOAT,Float.intBitsToFloat(0x3eaaaaab));
        p.set(new NamespacedKey("preservation_probe","bytes"),PersistentDataType.BYTE_ARRAY,new byte[]{0,1,-1,127,-128});
        var nested=p.getAdapterContext().newPersistentDataContainer();
        nested.set(OWNER,PersistentDataType.STRING,"nested-owner");
        p.set(new NamespacedKey("preservation_probe","nested"),PersistentDataType.TAG_CONTAINER,nested);
        item.setItemMeta(m);return item;
    }
    private void richEqual(ItemStack expected,ItemStack actual,String message) {
        require(actual!=null && actual.isSimilar(expected),message);
        var p=actual.getItemMeta().getPersistentDataContainer();
        require(Long.valueOf(9007199254740993L).equals(p.get(LONG,PersistentDataType.LONG)),"exact long");
        require(Float.floatToRawIntBits(p.get(FLOAT,PersistentDataType.FLOAT))==0x3eaaaaab,"exact float bits");
    }
    private BlockMenu menu(Block block) {
        var c=Slimefun.getDatabaseManager().getBlockDataController();
        var data=c.getBlockData(block.getLocation());
        require(data!=null,"saved block exists");
        if(!data.isDataLoaded())c.loadBlockData(data);
        require(data.getBlockMenu()!=null,"real block menu exists");
        return data.getBlockMenu();
    }
    private void verify() throws Exception {
        require(Set.of("seed","upgrade","restart").contains(phase),"known phase");
        require(Bukkit.getPluginManager().isPluginEnabled("BetterChests"),"addon enabled");
        World world=Bukkit.getWorlds().getFirst();world.getChunkAt(0,0).load();
        Block drawer=at(world,4), chest=at(world,5), corrupt=at(world,6);
        Path state=Path.of("probe-expectations.yml");
        YamlConfiguration saved=new YamlConfiguration();
        SimpleDrawer type=(SimpleDrawer)SlimefunItem.getById("BC_DRAWER_MAX");
        require(type!=null,"drawer id preserved");
        if(phase.equals("seed")) {
            require(!Files.exists(state),"fresh disposable world");
            create(drawer,"BC_DRAWER_MAX");create(chest,"BC_CHEST_27");create(corrupt,"BC_IE_STORAGE_UNIT_1");
            ItemStack rich=richItem();
            DrawerStorage.write(drawer,new DrawerData(rich,12345L));
            var data=Slimefun.getDatabaseManager().getBlockDataController().getBlockData(drawer.getLocation());
            data.setData("untouched-owner","owner-from-old-addon");
            data.setData("unknown-key","opaque historical data");
            ItemStack chestStack=rich.clone();chestStack.setAmount(37);menu(chest).replaceExistingItem(0,chestStack);
            var damaged=Slimefun.getDatabaseManager().getBlockDataController().getBlockData(corrupt.getLocation());
            damaged.setData("stored","not-a-number-preserve");
            ItemStack portable=type.getItem().clone();DrawerStorage.saveToPortableItem(portable,new DrawerData(rich,12345L));
            saved.set("expected-count",12345L);
            saved.set("item",Base64.getEncoder().encodeToString(rich.serializeAsBytes()));
            saved.set("portable",Base64.getEncoder().encodeToString(portable.serializeAsBytes()));
            saved.set("raw-drawer",data.getData(DrawerStorage.ITEM_KEY));
            saved.save(state.toFile());
            richEqual(rich,DrawerStorage.read(drawer).item(),"old writer readback");
            require(DrawerStorage.read(drawer).count()==12345L,"old count readback");
            require(menu(chest).getItemInSlot(0).getAmount()==37,"old inventory readback");
            return;
        }
        saved.load(state.toFile());
        ItemStack expected=ItemStack.deserializeBytes(Base64.getDecoder().decode(saved.getString("item")));
        long count=saved.getLong("expected-count");
        var controller=Slimefun.getDatabaseManager().getBlockDataController();
        var data=controller.getBlockData(drawer.getLocation());
        require(data!=null,"drawer survives process restart");if(!data.isDataLoaded())controller.loadBlockData(data);
        richEqual(expected,DrawerStorage.read(drawer).item(),"upgraded drawer identity");
        require(DrawerStorage.read(drawer).count()==count,"upgraded exact count");
        require(saved.getString("raw-drawer").equals(data.getData(DrawerStorage.ITEM_KEY)),"unchanged item encoding at startup");
        require("owner-from-old-addon".equals(data.getData("untouched-owner")),"owner field unchanged");
        require("opaque historical data".equals(data.getData("unknown-key")),"unknown field unchanged");
        richEqual(expected,menu(chest).getItemInSlot(0),"saved chest identity");
        require(menu(chest).getItemInSlot(0).getAmount()==37,"saved chest amount");
        ItemStack portable=ItemStack.deserializeBytes(Base64.getDecoder().decode(saved.getString("portable")));
        richEqual(expected,DrawerStorage.loadFromPortableItem(portable).item(),"old portable item reader");
        require(DrawerStorage.loadFromPortableItem(portable).count()==12345L,"old portable exact count");
        var context=expected.getItemMeta().getPersistentDataContainer().getAdapterContext();
        ByteArrayOutputStream legacyBytes=new ByteArrayOutputStream();
        try(var stream=new org.bukkit.util.io.BukkitObjectOutputStream(legacyBytes)){stream.writeObject(expected);}
        richEqual(expected,IEStorageUnit.ITEM_STACK.fromPrimitive(legacyBytes.toByteArray(),context),"retained historical IE object stream reader");
        richEqual(expected,IEStorageUnit.ITEM_STACK.fromPrimitive(expected.serializeAsBytes(),context),"retained native IE reader");
        Class<?> adapter=Class.forName("me.mmmjjkx.betterChests.compat.SlimefunBlockCompat");
        Method get=adapter.getMethod("getData",Location.class,String.class),set=adapter.getMethod("setData",Location.class,String.class,String.class);
        require("BC_DRAWER_MAX".equals(get.invoke(null,drawer.getLocation(),"id")),"real identity via new boundary");
        set.invoke(null,drawer.getLocation(),"probe-transient","42");
        require("42".equals(get.invoke(null,drawer.getLocation(),"probe-transient")),"real metadata writer");
        set.invoke(null,drawer.getLocation(),"probe-transient",null);
        require(get.invoke(null,drawer.getLocation(),"probe-transient")==null,"real metadata deletion");
        Block missing=at(world,7);
        try{set.invoke(null,missing.getLocation(),"stored","1");throw new AssertionError("missing write silently accepted");}
        catch(InvocationTargetException refused){require(refused.getCause() instanceof IllegalStateException,"missing write refused");}
        require(controller.getBlockData(missing.getLocation())==null,"failed write creates no phantom block");
        ItemStack incoming=expected.clone();incoming.setAmount(5);var result=type.addItem(drawer.getLocation(),incoming);
        require(result.left() && result.right()==0,"real Cargo accepts exact stack");
        require(incoming.getType()==Material.AIR,"legacy Cargo retains in-place AIR protocol");
        ItemStack taken=type.takeItem(drawer.getLocation(),3);
        richEqual(expected,taken,"real withdrawal preserves item");require(taken.getAmount()==3,"exact withdrawal");
        require(type.getStoringItemCount(drawer.getLocation())==count+2,"real long count accounting");
        ItemStack different=expected.clone();different.editMeta(m->m.getPersistentDataContainer().set(OWNER,PersistentDataType.STRING,"different owner"));different.setAmount(9);
        var reject=type.addItem(drawer.getLocation(),different);
        require(!reject.left() && reject.right()==9 && different.getAmount()==9,"different owner item not consumed");
        var damaged=controller.getBlockData(corrupt.getLocation());if(!damaged.isDataLoaded())controller.loadBlockData(damaged);
        require("not-a-number-preserve".equals(damaged.getData("stored")),"malformed persisted count not normalized");
        int loadedChunks=world.getLoadedChunks().length;
        Map<String,String> before=new HashMap<>(data.getAllData());
        Class<?> doctor=Class.forName("me.mmmjjkx.betterChests.diagnostics.BetterChestsDoctor");
        Method scan=doctor.getDeclaredMethod("run",boolean.class);scan.setAccessible(true);scan.invoke(null,false);
        require(before.equals(data.getAllData()),"Doctor scan leaves metadata untouched");
        require(loadedChunks==world.getLoadedChunks().length,"Doctor scan loads no extra chunks");
        saved.set("expected-count",count+2);saved.set("raw-drawer",data.getData(DrawerStorage.ITEM_KEY));saved.save(state.toFile());
        require("owner-from-old-addon".equals(data.getData("untouched-owner")),"operations retain owner");
    }
}
