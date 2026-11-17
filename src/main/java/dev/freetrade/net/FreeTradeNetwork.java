package dev.freetrade.net;

import java.util.ArrayList;
import java.util.List;

import dev.freetrade.FreeTrade;
import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.currency.Currency;
import dev.freetrade.market.Market;
import dev.freetrade.menu.MachineMenu;
import dev.freetrade.menu.TraderMenu;
import dev.freetrade.server.FreeTradeServer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class FreeTradeNetwork {
    private FreeTradeNetwork() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeTrade.MODID, name));
    }

    /** Server -> client: money items and their value (milli). */
    public record CurrencySync(List<String> items, List<Long> values, boolean bank) implements CustomPacketPayload {
        public static final Type<CurrencySync> TYPE = FreeTradeNetwork.payloadType("currency");
        public static final StreamCodec<ByteBuf, CurrencySync> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CurrencySync::items,
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), CurrencySync::values,
                ByteBufCodecs.BOOL, CurrencySync::bank,
                CurrencySync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the catalog. {@code openGuide} opens the guide book screen. */
    public record CatalogPayload(boolean openGuide, Catalog.Header header, List<Catalog.Entry> entries)
            implements CustomPacketPayload {
        public static final Type<CatalogPayload> TYPE = FreeTradeNetwork.payloadType("catalog");
        public static final StreamCodec<ByteBuf, CatalogPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, CatalogPayload::openGuide,
                Catalog.Header.CODEC, CatalogPayload::header,
                Catalog.Entry.CODEC.apply(ByteBufCodecs.list()), CatalogPayload::entries,
                CatalogPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: ask for a fresh catalog (book refresh / buy tab). */
    public record CatalogRequest(boolean openGuide) implements CustomPacketPayload {
        public static final Type<CatalogRequest> TYPE = FreeTradeNetwork.payloadType("catalog_request");
        public static final StreamCodec<ByteBuf, CatalogRequest> CODEC =
                ByteBufCodecs.BOOL.map(CatalogRequest::new, CatalogRequest::openGuide);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: set the ghost item of a machine. */
    public record SetTarget(int containerId, ItemStack stack) implements CustomPacketPayload {
        public static final Type<SetTarget> TYPE = FreeTradeNetwork.payloadType("set_target");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetTarget> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetTarget::containerId,
                ItemStack.OPTIONAL_STREAM_CODEC, SetTarget::stack,
                SetTarget::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: trade with the representative. sell: item/qty ignored. */
    public record TraderAction(int containerId, boolean sell, String item, int quantity, boolean bank)
            implements CustomPacketPayload {
        public static final Type<TraderAction> TYPE = FreeTradeNetwork.payloadType("trader_action");
        public static final StreamCodec<ByteBuf, TraderAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TraderAction::containerId,
                ByteBufCodecs.BOOL, TraderAction::sell,
                ByteBufCodecs.STRING_UTF8, TraderAction::item,
                ByteBufCodecs.VAR_INT, TraderAction::quantity,
                ByteBufCodecs.BOOL, TraderAction::bank,
                TraderAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(CurrencySync.TYPE, CurrencySync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientHooks.onCurrency(p)));
        r.playToClient(CatalogPayload.TYPE, CatalogPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientHooks.onCatalog(p)));
        r.playToServer(CatalogRequest.TYPE, CatalogRequest.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) sendCatalog(sp, p.openGuide());
        }));
        r.playToServer(SetTarget.TYPE, SetTarget.CODEC, FreeTradeNetwork::onSetTarget);
        r.playToServer(TraderAction.TYPE, TraderAction.CODEC, FreeTradeNetwork::onTraderAction);
    }

    private static void onSetTarget(SetTarget p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof MachineMenu m
                    && m.containerId == p.containerId() && m.stillValid(sp)) {
                if (!m.setTarget(p.stack())) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("freetrade.block.target_locked"), true);
                }
            }
        });
    }

    private static void onTraderAction(TraderAction p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TraderMenu m
                    && m.containerId == p.containerId() && m.stillValid(sp)) {
                if (p.sell()) m.sell(sp, p.bank());
                else m.buy(sp, p.item(), p.quantity(), p.bank());
            }
        });
    }

    // ------------------------------------------------------------------ sending

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static CurrencySync currencyPayload() {
        List<String> ids = new ArrayList<>();
        List<Long> vals = new ArrayList<>();
        for (var d : Currency.denominations()) {
            ids.add(dev.freetrade.util.ItemKeys.id(d.item()));
            vals.add(d.milli());
        }
        return new CurrencySync(ids, vals, dev.freetrade.currency.Wallet.bankAvailable());
    }

    public static void syncCurrency(ServerPlayer player) {
        send(player, currencyPayload());
    }

    public static void syncCurrencyAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncCurrency(p);
    }

    public static void sendCatalog(ServerPlayer player, boolean openGuide) {
        Market m = FreeTradeServer.market();
        if (m == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("freetrade.error.offline"), false);
            return;
        }
        Catalog.Header header = Catalog.header(m);
        // the book's catalog can be hidden from regular players by the server admin (OPs always see it)
        boolean hidden = openGuide && !FreeTradeConfig.guideShowCatalog && !player.hasPermissions(2);
        header.config().put("catalog_hidden", Boolean.toString(hidden));
        send(player, new CatalogPayload(openGuide, header, hidden ? List.of() : Catalog.build(m)));
    }
}
