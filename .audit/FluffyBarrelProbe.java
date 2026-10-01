package io.github.wickidcow.probe;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.ncbpfluffybear.fluffymachines.FluffyMachines;
import io.ncbpfluffybear.fluffymachines.items.Barrel;
import io.ncbpfluffybear.fluffymachines.utils.Utils;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real storage and entities; UI methods use an explicitly synthetic no-network player. */
public final class FluffyBarrelProbe extends JavaPlugin {
    private final String phase=System.getProperty("probe.phase","invalid");
    private int assertions,opens;private boolean online=true;
    private Location position;private Inventory opened;private Player player;
    private World world;private Barrel barrel;private Method tick;
    private static final NamespacedKey OWNER=new NamespacedKey("preservation_probe","owner");
    private void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    private void fail(Throwable t){getLogger().log(java.util.logging.Level.SEVERE,"PROBE_FAIL "+phase,t);}
    private void pass(){getLogger().info("PROBE_PASS "+phase+" assertions="+assertions);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable t){fail(t);}},80L);}
    private Block at(int x){return world.getBlockAt(x,90,8);}
    private BlockMenu menu(Block b){
        var controller=Slimefun.getDatabaseManager().getBlockDataController();var d=controller.getBlockData(b.getLocation());
        check(d!=null,"saved block exists");if(!d.isDataLoaded())controller.loadBlockData(d);
        check(d.getBlockMenu()!=null,"actual block menu exists");return d.getBlockMenu();
    }
    private void create(Block b){b.setType(barrel.getItem().getType(),false);Slimefun.getDatabaseManager().getBlockDataController().createBlock(b.getLocation(),barrel.getId());Slimefun.getTickerTask().disableTicker(b.getLocation());}
    private void tick(Block b)throws Exception{tick.invoke(barrel,b);}
    private ItemStack rich(){
        ItemStack item=new ItemStack(Material.DIAMOND);var m=item.getItemMeta();m.displayName(Component.text("Saved barrel owner"));
        m.lore(List.of(Component.text("Old lore"),Component.empty(),Component.text("Keep me")));
        var p=m.getPersistentDataContainer();p.set(OWNER,PersistentDataType.STRING,"old-owner");
        p.set(new NamespacedKey("preservation_probe","long"),PersistentDataType.LONG,9007199254740993L);
        p.set(new NamespacedKey("preservation_probe","float"),PersistentDataType.FLOAT,Float.intBitsToFloat(0x3eaaaaab));
        p.set(new NamespacedKey("preservation_probe","bytes"),PersistentDataType.BYTE_ARRAY,new byte[]{-128,-1,0,1,127});
        var nested=p.getAdapterContext().newPersistentDataContainer();nested.set(OWNER,PersistentDataType.STRING,"nested-owner");
        p.set(new NamespacedKey("preservation_probe","nested"),PersistentDataType.TAG_CONTAINER,nested);item.setItemMeta(m);return item;
    }
    private ItemStack amount(ItemStack i,int n){ItemStack r=i.clone();r.setAmount(n);return r;}
    private long total(Block b,BlockMenu m){long n=barrel.getStored(b);for(int slot:new int[]{19,20,24,25}){ItemStack i=m.getItemInSlot(slot);if(i!=null&&!i.getType().isAir())n+=i.getAmount();}return n;}
    private void equal(ItemStack expected,ItemStack actual,String why){check(actual!=null&&expected.isSimilar(actual),why);}
    private void clear(Block b,BlockMenu m){for(int slot:new int[]{19,20,24,25})m.replaceExistingItem(slot,null);barrel.setStored(b,0);m.replaceExistingItem(31,new ItemStack(Material.BARRIER));}
    private void run()throws Exception{
        check(Bukkit.getPluginManager().isPluginEnabled("FluffyMachines"),"addon enabled");
        world=Bukkit.getWorlds().getFirst();world.getChunkAt(0,0).load();
        barrel=(Barrel)SlimefunItem.getById("SMALL_FLUFFY_BARREL");check(barrel!=null,"original barrel id");
        tick=Barrel.class.getDeclaredMethod("tick",Block.class);tick.setAccessible(true);
        Block b=at(4);Path state=Path.of("fluffy-expectations.yml");YamlConfiguration saved=new YamlConfiguration();
        if(phase.equals("seed")){
            create(b);BlockMenu m=menu(b);ItemStack expected=rich();m.replaceExistingItem(19,expected.clone());tick(b);
            check(total(b,m)==1,"old producer item conserved");check(barrel.getStored(b)==0,"old single item moved into output");
            check(m.getItemInSlot(31).getType()==Material.BARRIER,"original first-item registration defect reproduced");
            saved.set("item",Base64.getEncoder().encodeToString(expected.serializeAsBytes()));saved.set("count",1L);saved.save(state.toFile());
            var d=Slimefun.getDatabaseManager().getBlockDataController().getBlockData(b.getLocation());d.setData("unknown-owner-field","preserve-me");
            pass();return;
        }
        check(phase.equals("upgrade")||phase.equals("restart"),"known phase");saved.load(state.toFile());
        ItemStack expected=ItemStack.deserializeBytes(Base64.getDecoder().decode(saved.getString("item")));
        Slimefun.getTickerTask().disableTicker(b.getLocation());BlockMenu m=menu(b);
        check(total(b,m)==saved.getLong("count"),"old exact persisted item count");
        tick(b);equal(expected,barrel.getStoredItem(b),"buffer restores exact item identity");
        check(barrel.visibleContents(b).safe()&&barrel.visibleContents(b).amount()==total(b,m),"both buffers included in visible total");
        check("preserve-me".equals(Slimefun.getDatabaseManager().getBlockDataController().getBlockData(b.getLocation()).getData("unknown-owner-field")),"unknown owner metadata preserved");
        ItemStack other=expected.clone();other.editMeta(meta->meta.getPersistentDataContainer().set(OWNER,PersistentDataType.STRING,"another-owner"));
        m.replaceExistingItem(19,amount(other,3));long before=total(b,m);tick(b);
        equal(other,m.getItemInSlot(19),"different owner rejected while output holds registered item");check(m.getItemInSlot(19).getAmount()==3&&total(b,m)==before,"rejected items conserved");
        m.replaceExistingItem(19,amount(expected,5));before=total(b,m);tick(b);check(total(b,m)==before,"matching insertion conserved");
        for(int slot:new int[]{24,25})if(m.getItemInSlot(slot)!=null)equal(expected,m.getItemInSlot(slot),"output typed metadata unchanged");
        Block test=at(6);if(Slimefun.getDatabaseManager().getBlockDataController().getBlockData(test.getLocation())==null)create(test);
        Slimefun.getTickerTask().disableTicker(test.getLocation());BlockMenu t=menu(test);clear(test,t);
        t.replaceExistingItem(24,amount(expected,64));t.replaceExistingItem(25,amount(expected,17));tick(test);
        check(barrel.visibleContents(test).amount()==81,"both output stacks counted");equal(expected,barrel.getStoredItem(test),"both-buffer repair exact identity");
        t.replaceExistingItem(24,null);tick(test);equal(expected,barrel.getStoredItem(test),"second output alone keeps registration");
        t.replaceExistingItem(25,null);tick(test);check(t.getItemInSlot(31).getType()==Material.BARRIER,"truly empty barrel clears registration");
        t.replaceExistingItem(19,other.clone());tick(test);equal(other,barrel.getStoredItem(test),"empty barrel accepts new type from first item");
        clear(test,t);t.replaceExistingItem(24,expected.clone());t.replaceExistingItem(25,other.clone());t.replaceExistingItem(19,amount(expected,9));
        before=total(test,t);tick(test);check(!barrel.visibleContents(test).safe(),"conflicting buffers classified unsafe");
        check(total(test,t)==before&&t.getItemInSlot(31).getType()==Material.BARRIER&&t.getItemInSlot(19).getAmount()==9,"ambiguous buffers untouched");
        clear(test,t);barrel.setStored(test,123);t.replaceExistingItem(24,expected.clone());tick(test);
        check(!barrel.visibleContents(test).safe()&&barrel.getStored(test)==123&&t.getItemInSlot(31).getType()==Material.BARRIER,"unknown positive contents not assigned a guessed identity");
        clear(test,t);barrel.setStored(test,-7);t.replaceExistingItem(19,expected.clone());tick(test);check(barrel.getStored(test)==-7&&t.getItemInSlot(19).getAmount()==1,"negative count not normalized or consumed");
        clear(test,t);int cap=barrel.getCapacity(test);barrel.setStored(test,cap-1);t.replaceExistingItem(31,Utils.keyItem(expected));
        t.replaceExistingItem(24,amount(expected,64));t.replaceExistingItem(25,amount(expected,64));t.replaceExistingItem(19,amount(expected,3));
        tick(test);check(barrel.getStored(test)==cap&&t.getItemInSlot(19).getAmount()==2,"capacity arithmetic preserves partial input");
        var data=Slimefun.getDatabaseManager().getBlockDataController().getBlockData(test.getLocation());data.setData("trash","false");tick(test);
        check(t.getItemInSlot(19).getAmount()==2&&barrel.getStored(test)==cap,"disabled overflow purge preserves excess");
        data.setData("trash","true");tick(test);check(t.getItemInSlot(19)==null&&barrel.getStored(test)==cap,"enabled overflow purge retains existing behavior");
        clear(test,t);
        saved.set("count",total(b,m));saved.save(state.toFile());
        ui(b,m,expected);
    }
    private void ui(Block b,BlockMenu menu,ItemStack expected)throws Exception{
        position=b.getLocation().add(.5,.5,-2);UUID id=UUID.fromString("f3e9d521-1a4c-4b51-baa1-68e5b8b49001");
        player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "isOnline","isValid","hasPermission","isOp"->online;
            case "getWorld"->world;
            case "getLocation","getEyeLocation"->position.clone();
            case "getUniqueId"->id;
            case "getName"->"SyntheticBarrelViewer";
            case "getGameMode"->GameMode.SURVIVAL;
            case "openInventory"->{opens++;opened=(Inventory)args[0];yield null;}
            case "showEntity","hideEntity","sendMessage","sendActionBar","closeInventory"->null;
            case "hashCode"->12345;
            case "equals"->proxy==args[0];
            case "toString"->"Explicit synthetic no-network barrel viewer";
            default->{Class<?> r=method.getReturnType();yield r==boolean.class?false:r==int.class?0:r==long.class?0L:r==float.class?0F:r==double.class?0D:null;}
        });
        Class<?> display=Class.forName("io.ncbpfluffybear.fluffymachines.utils.BarrelDisplayManager");
        display.getMethod("update",Block.class,Barrel.class).invoke(null,b,barrel);
        var icons=world.getNearbyEntities(b.getLocation().add(.5,.5,.5),2,2,2).stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList();
        check(icons.stream().anyMatch(i->i.getItemStack().isSimilar(expected)&&i.getItemStack().getAmount()==1),"single buffered item has one-item native display");
        Class<?> hover=Class.forName("io.ncbpfluffybear.fluffymachines.utils.BarrelHoverNameManager");
        Method show=hover.getDeclaredMethod("showLabel",FluffyMachines.class,Player.class,Block.class,ItemStack.class);show.setAccessible(true);
        byte[] before=expected.serializeAsBytes();show.invoke(null,FluffyMachines.getInstance(),player,b,expected);
        Field labels=hover.getDeclaredField("LABELS");labels.setAccessible(true);Map<?,?> map=(Map<?,?>)labels.get(null);TextDisplay label=(TextDisplay)map.get(id);
        check(label!=null&&label.isValid()&&!label.isVisibleByDefault()&&!label.isPersistent(),"private nonpersistent hover entity");
        check(label.text().equals(expected.getItemMeta().displayName()),"native item name preserved on hover");check(Arrays.equals(before,expected.serializeAsBytes()),"hover never mutates source item");
        hover.getMethod("shutdown").invoke(null);check(!label.isValid()&&map.isEmpty(),"hover shutdown removes entities and state");
        hover.getMethod("initialize").invoke(null);
        Block front=b.getRelative(BlockFace.NORTH);front.setType(Material.AIR,false);
        ItemFrame frame=world.spawn(front.getLocation().add(.5,.5,.5),ItemFrame.class,f->{f.setFacingDirection(BlockFace.NORTH,true);f.setInvulnerable(true);f.setPersistent(false);});
        check(frame.isValid()&&frame.getLocation().getBlock().getRelative(frame.getAttachedFace()).equals(b),"actual frame is attached to barrel");
        var listener=new io.ncbpfluffybear.fluffymachines.listeners.BarrelItemFrameListener();
        var cancelled=new PlayerInteractEntityEvent(player,frame,EquipmentSlot.HAND);cancelled.setCancelled(true);listener.onBarrelItemFrameClick(cancelled);
        var offhand=new PlayerInteractEntityEvent(player,frame,EquipmentSlot.OFF_HAND);listener.onBarrelItemFrameClick(offhand);check(!offhand.isCancelled(),"off-hand ignored");
        position=position.clone().add(100,0,0);var far=new PlayerInteractEntityEvent(player,frame,EquipmentSlot.HAND);listener.onBarrelItemFrameClick(far);check(!far.isCancelled(),"distant frame click ignored");
        position=b.getLocation().add(.5,.5,-2);var allowed=new PlayerInteractEntityEvent(player,frame,EquipmentSlot.HAND);listener.onBarrelItemFrameClick(allowed);check(allowed.isCancelled(),"allowed frame rotation cancelled");
        Bukkit.getScheduler().runTaskLater(this,()->{
            try{
                check(opens==1&&opened==menu.toInventory(),"one allowed click opens exact barrel menu once");
                var stale=new PlayerInteractEntityEvent(player,frame,EquipmentSlot.HAND);listener.onBarrelItemFrameClick(stale);check(stale.isCancelled(),"valid initial deferred click");online=false;
                Bukkit.getScheduler().runTaskLater(this,()->{
                    try{check(opens==1,"offline deferred target does not open");frame.remove();hover.getMethod("shutdown").invoke(null);display.getMethod("shutdown").invoke(null);equal(expected,barrel.getStoredItem(b),"UI lifecycle preserves stored identity");pass();}
                    catch(Throwable error){fail(error);}
                },3L);
            }catch(Throwable error){fail(error);}
        },3L);
    }
}
