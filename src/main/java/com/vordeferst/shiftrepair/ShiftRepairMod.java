package com.vordeferst.shiftrepair;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(ShiftRepairMod.MODID)
public class ShiftRepairMod {
    public static final String MODID = "shiftrepair";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Block> GENERATOR = BLOCKS.register("generator", () -> new GeneratorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(3.5F).sound(SoundType.METAL)));
    public static final RegistryObject<Item> GENERATOR_ITEM = ITEMS.register("generator", () -> new BlockItem(GENERATOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> WRENCH = ITEMS.register("wrench", () -> new Item(new Item.Properties().durability(250)));
    private static final Map<UUID, BlockPos> REPAIR_TARGETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> REPAIR_TICKS = new ConcurrentHashMap<>();

    public ShiftRepairMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getLevel().getBlockState(event.getPos()).is(GENERATOR.get())) {
            if (!player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal("Присядь (Shift) и нажми ПКМ по генератору, затем держи Shift рядом 45 секунд."), true);
                return;
            }
            REPAIR_TARGETS.put(player.getUUID(), event.getPos().immutable());
            REPAIR_TICKS.put(player.getUUID(), 0);
            player.displayClientMessage(Component.literal("Ремонт начат: 0%"), true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        UUID id = player.getUUID();
        BlockPos target = REPAIR_TARGETS.get(id);
        if (target == null) return;
        if (!player.isShiftKeyDown() || player.distanceToSqr(target.getX()+0.5, target.getY()+0.5, target.getZ()+0.5) > 25 || !player.level().getBlockState(target).is(GENERATOR.get())) {
            REPAIR_TARGETS.remove(id);
            REPAIR_TICKS.remove(id);
            player.displayClientMessage(Component.literal("Ремонт остановлен."), true);
            return;
        }
        int ticks = REPAIR_TICKS.getOrDefault(id, 0);
        double speed = player.getMainHandItem().is(WRENCH.get()) ? 1.0185 : 1.0;
        ticks += (int)Math.max(1, Math.round(speed));
        REPAIR_TICKS.put(id, ticks);
        if (ticks % 20 == 0) player.displayClientMessage(Component.literal("Ремонт генератора: " + Math.min(100, ticks * 100 / 900) + "%"), true);
        if (ticks >= 900) {
            REPAIR_TARGETS.remove(id);
            REPAIR_TICKS.remove(id);
            player.displayClientMessage(Component.literal("Генератор починен!"), true);
            player.level().playSound(null, target, net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.2F);
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY()+0.8, player.getZ(), 8, 0.35, 0.25, 0.35, 0.02);
        }
    }

    public static class GeneratorBlock extends Block {
        public GeneratorBlock(BlockBehaviour.Properties props) { super(props); }
        @Override
        public InteractionResult use(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            return InteractionResult.SUCCESS;
        }
    }
}
