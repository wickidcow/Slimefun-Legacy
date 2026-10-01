package io.github.wickidcow.probe;

import com.balugaq.buildingstaff.compat.BlockCompatibility;
import com.balugaq.buildingstaff.compat.CustomBlockPlacement;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.util.*;

/** Actual providers/world/inventories with an explicitly synthetic no-network Player facade. */
public final class BuildingStaffProviderProbe extends JavaPlugin {
    private int assertions;
    private World world;
    private Player player;
    private GameMode mode=GameMode.SURVIVAL;
    private Object registry, researchRegistry;
    private Method place;
    private final String phase=System.getProperty("probe.phase","initial");
    private void check(boolean value,String label) { assertions++;if(!value)throw new AssertionError(label); }
    private static Object call(Object target,String name,Object... args) throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        outer:for(Method method:type.getMethods()) {
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            Class<?>[] types=method.getParameterTypes();
            for(int i=0;i<types.length;i++) {
                Class<?> t=types[i];if(t.isPrimitive())t=t==int.class?Integer.class:t==double.class?Double.class:t==boolean.class?Boolean.class:t==float.class?Float.class:t==long.class?Long.class:t;
                if(args[i]!=null&&!t.isInstance(args[i]))continue outer;
            }
            try{return method.invoke(target instanceof Class<?>?null:target,args);}
            catch(InvocationTargetException failure){throw new Exception(name,failure.getCause());}
        }
        throw new NoSuchMethodException(type+"."+name+Arrays.toString(args));
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this,()->{
            try { run();getLogger().info("PROBE_PASS "+phase+" assertions="+assertions); }
            catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"PROBE_FAIL "+phase,error);}
        },100L);
    }
    private Object rb(Block block) throws Exception {return call(Class.forName("io.github.pylonmc.rebar.block.BlockStorage"),"get",block);}
    private ItemStack item(String key) throws Exception {
        Object schema=call(registry,"getOrThrow",NamespacedKey.fromString(key));
        return (ItemStack)call(schema,"createNewItemStack",1);
    }
    private void research(boolean granted) throws Exception {
        List<?> list=granted?new ArrayList<>((Collection<?>)call(researchRegistry,"getValues")):List.of();
        call(Class.forName("io.github.pylonmc.rebar.item.research.Research"),"setResearches",player,list);
    }
    private Block block(int x) {return world.getBlockAt(x,90,8);}
    private void stock(ItemStack item,int count) {
        player.getInventory().clear();player.getInventory().setItem(0,new ItemStack(Material.STICK));
        for(int i=1;i<=count;i++)player.getInventory().setItem(i,item.clone());
    }
    private int count(ItemStack template){return CustomBlockPlacement.countItems(player.getInventory(),template,999);}
    private String place(Block block,ItemStack item) throws Exception {
        return String.valueOf(place.invoke(null,Bukkit.getPluginManager().getPlugin("BuildingStaff"),player,block,BlockFace.NORTH,item));
    }
    private Listener placeListener(EventPriority priority,java.util.function.Consumer<BlockPlaceEvent> action) {
        Listener listener=new Listener(){};
        Bukkit.getPluginManager().registerEvent(BlockPlaceEvent.class,listener,priority,(ignored,e)->action.accept((BlockPlaceEvent)e),this,false);
        return listener;
    }
    private Player makePlayer() throws Exception {
        Object server=call(Bukkit.getServer(),"getServer"),level=call(world,"getHandle");
        Object profile=Class.forName("com.mojang.authlib.GameProfile").getConstructor(UUID.class,String.class)
                .newInstance(UUID.fromString("f3e9d521-1a4c-4b51-baa1-68e5b8b49000"),"StaffProbe");
        Class<?> infoType=Class.forName("net.minecraft.server.level.ClientInformation");
        Object info=call(infoType,"createDefault");
        Class<?> sp=Class.forName("net.minecraft.server.level.ServerPlayer");Object handle=null;
        for(Constructor<?> c:sp.getConstructors()) {
            if(c.getParameterCount()==4 && c.getParameterTypes()[0].isInstance(server)
                    && c.getParameterTypes()[1].isInstance(level)) {handle=c.newInstance(server,level,profile,info);break;}
        }
        if(handle==null)throw new IllegalStateException("Unrecognized ServerPlayer constructor "+Arrays.toString(sp.getConstructors()));
        call(handle,"setPos",8.0,91.0,8.0);
        Player delegate=(Player)call(handle,"getBukkitEntity");
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,args)->{
            String n=m.getName();
            if(n.equals("isOnline")||n.equals("isConnected")||n.equals("isOp")||n.equals("hasPermission"))return true;
            if(n.equals("getGameMode"))return mode;
            if(n.equals("getWorld"))return world;
            if(n.equals("getLocation")&&m.getParameterCount()==0)return new Location(world,8,91,8);
            if(n.equals("getEyeLocation"))return new Location(world,8,92.62,8);
            if(n.equals("getYaw")||n.equals("getPitch"))return 0F;
            if(n.equals("hashCode"))return 1234567;
            if(n.equals("equals"))return proxy==args[0];
            if(n.equals("toString"))return "Synthetic-no-network-StaffProbe";
            if(n.equals("setOp")||n.startsWith("send")||n.startsWith("play")||n.startsWith("show")||n.startsWith("hide"))return null;
            try{return m.invoke(delegate,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    private void run() throws Exception {
        check(Bukkit.getPluginManager().isPluginEnabled("BuildingStaff"),"BuildingStaff enabled");
        check(Bukkit.getPluginManager().isPluginEnabled("Rebar")&&Bukkit.getPluginManager().isPluginEnabled("Pylon"),"actual providers enabled");
        world=Bukkit.getWorlds().getFirst();world.getChunkAt(0,0).load();world.getChunkAt(1,0).load();
        Class<?> registries=Class.forName("io.github.pylonmc.rebar.registry.RebarRegistry");
        registry=registries.getField("ITEMS").get(null);researchRegistry=registries.getField("RESEARCHES").get(null);
        player=makePlayer();research(true);
        place=CustomBlockPlacement.class.getDeclaredMethod("placeOne",org.bukkit.plugin.Plugin.class,Player.class,Block.class,BlockFace.class,ItemStack.class);place.setAccessible(true);
        ItemStack tank=item("pylon:portable_fluid_tank_wood"),cargo=item("pylon:cargo_buffer");
        if(phase.equals("restart")) {
            Object saved=rb(block(4));check(saved!=null,"stateful tank registration survived restart");
            check(((Number)call(saved,"getFluidAmount")).doubleValue()==1234.5,"fluid count survived restart");
            check("pylon:water".equals(String.valueOf(call(call(saved,"getFluidType"),"getKey"))),"fluid identity survived restart");
            check(rb(block(5))!=null,"copied stateful tank survived restart");
            check(((Number)call(rb(block(5)),"getFluidAmount")).doubleValue()==1234.5,"copied amount survived restart");
            check(rb(block(10))!=null,"vetoed rollback retains registered block across restart");
            return;
        }
        for(int x=2;x<=14;x++)block(x).setType(Material.AIR,false);
        stock(tank,2);check(place(block(4),tank).equals("PLACED"),"real provider placement");check(count(tank)==1,"exactly one item charged");
        check(BlockCompatibility.isPlacedFromItem(block(4),tank),"provider identity verified");
        Object original=rb(block(4));Object fluids=registries.getField("FLUIDS").get(null);Object water=call(fluids,"getOrThrow",NamespacedKey.fromString("pylon:water"));
        call(original,"setFluidType",water);call(original,"setFluid",1234.5);
        ItemStack filled=BlockCompatibility.getPlacementItem(block(4),player);check(filled!=null&&!filled.isSimilar(tank),"provider pick retains fluid state");
        stock(tank,2);check(place(block(5),filled).equals("NO_PAYMENT"),"empty tank cannot pay for filled tank");check(rb(block(5))==null,"unpaid target untouched");
        stock(filled,1);check(place(block(5),filled).equals("PLACED"),"exact filled tank placement");
        check(((Number)call(rb(block(5)),"getFluidAmount")).doubleValue()==1234.5,"placed exact fluid amount");
        check(((Number)call(original,"getFluidAmount")).doubleValue()==1234.5,"source state not consumed or modified");
        stock(tank,0);player.getInventory().setItemInOffHand(tank);check(count(tank)==0,"off-hand not counted");
        check(place(block(6),tank).equals("NO_PAYMENT")&&rb(block(6))==null,"off-hand cannot pay");
        stock(tank,1);block(7).setType(Material.WATER,false);String waterState=block(7).getBlockData().getAsString();
        Listener cancel=placeListener(EventPriority.HIGHEST,e->{if(e.getBlock().equals(block(7)))e.setCancelled(true);});
        check(place(block(7),tank).equals("ROLLED_BACK"),"cancelled placement rolled back");HandlerList.unregisterAll(cancel);
        check(count(tank)==1&&rb(block(7))==null&&waterState.equals(block(7).getBlockData().getAsString()),"cancelled water target and exact payment restored");
        stock(tank,1);Listener late=placeListener(EventPriority.MONITOR,e->{if(e.getBlock().equals(block(9)))e.setCancelled(true);});
        check(place(block(9),tank).equals("ROLLED_BACK"),"actual already-created provider rollback");HandlerList.unregisterAll(late);
        check(rb(block(9))==null&&block(9).getType().isAir()&&count(tank)==1,"provider rollback verified before refund");
        stock(tank,1);Listener veto=new Listener(){};
        @SuppressWarnings("unchecked") Class<? extends Event> pre=(Class<? extends Event>)Class.forName("io.github.pylonmc.rebar.event.PreRebarBlockBreakEvent");
        Bukkit.getPluginManager().registerEvent(pre,veto,EventPriority.HIGHEST,(ignored,e)->((Cancellable)e).setCancelled(true),this,false);
        late=placeListener(EventPriority.MONITOR,e->{if(e.getBlock().equals(block(10)))e.setCancelled(true);});
        check(place(block(10),tank).equals("RECOVERY_REQUIRED"),"vetoed provider rollback is not refunded");HandlerList.unregisterAll(late);HandlerList.unregisterAll(veto);
        check(rb(block(10))!=null&&block(10).getType()==tank.getType()&&count(tank)==0,"veto preserves registered state and payment");
        stock(tank,1);research(false);String denied=place(block(11),tank);research(true);
        check(denied.equals("ROLLED_BACK")&&rb(block(11))==null&&count(tank)==1,"provider research cancellation respected");
        mode=GameMode.CREATIVE;stock(tank,0);check(place(block(12),tank).equals("PLACED")&&count(tank)==0,"creative placement has no payment");mode=GameMode.SURVIVAL;
        stock(cargo,1);check(place(block(13),cargo).equals("PLACED"),"cargo buffer placement");Object buffer=rb(block(13));
        Map<?,?> inventories=(Map<?,?>)call(buffer,"getVirtualInventories");Object inventory=inventories.get("inventory");
        ItemStack payload=new ItemStack(Material.DIAMOND,37);call(inventory,"setItem",null,0,payload);
        Map<?,?> held=(Map<?,?>)call(buffer,"getHeldEntities");Set<UUID> entityIds=new HashSet<>();for(Object value:held.values())entityIds.add((UUID)value);
        check(!entityIds.isEmpty(),"actual cargo display entities exist");
        int before=world.getEntitiesByClass(Item.class).stream().filter(e->e.getItemStack().getType()==Material.DIAMOND).mapToInt(e->e.getItemStack().getAmount()).sum();
        Listener breakCancel=new Listener(){};Bukkit.getPluginManager().registerEvent(BlockBreakEvent.class,breakCancel,EventPriority.HIGHEST,(ignored,e)->((BlockBreakEvent)e).setCancelled(true),this,false);
        CustomBlockPlacement.breakCustom(this,player,block(13));HandlerList.unregisterAll(breakCancel);check(rb(block(13))==buffer,"cancelled break leaves provider object intact");
        CustomBlockPlacement.breakCustom(this,player,block(13));check(rb(block(13))==null&&block(13).getType().isAir(),"provider owns custom break");
        int after=world.getEntitiesByClass(Item.class).stream().filter(e->e.getItemStack().getType()==Material.DIAMOND).mapToInt(e->e.getItemStack().getAmount()).sum();check(after-before==37,"cargo inventory drops exactly once");
        check(entityIds.stream().allMatch(id->Bukkit.getEntity(id)==null||!Bukkit.getEntity(id).isValid()),"cargo model entities removed");
        check(BlockCompatibility.isSameBlockType(block(4),block(5)),"matching custom identities match");
        block(14).setType(tank.getType(),false);check(!BlockCompatibility.isSameBlockType(block(4),block(14)),"vanilla backing material is not a custom match");
        check(count(tank)==0,"final inventory state accounted for");
    }
}
