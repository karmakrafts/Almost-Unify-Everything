/*
 * Copyright 2024 Karma Krafts & associates
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.karma.unifyeverything;

import com.almostreliable.unified.api.AlmostUnifiedLookup;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Alexander Hinze
 * @since 09/10/2024
 */
@Mod(AlmostUnifyEverything.MODID)
public class AlmostUnifyEverything {
    public static final String MODID = "unifyeverything";
    public static final Logger LOGGER = LogManager.getLogger("AUE");

    public AlmostUnifyEverything() {
        final var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(this::onRegisterCommands);
    }

    public static ItemStack unify(final ItemStack stack) {
        final var item = AlmostUnifiedLookup.INSTANCE.getReplacementForItem(stack.getItem());
        if (item == null || item == stack.getItem()) {
            return stack;
        }
        return new ItemStack(item, stack.getCount());
    }

    public static int unifyInventory(final Container container) {
        var unifiedItems = 0;
        for (var i = 0; i < container.getContainerSize(); ++i) {
            final var input = container.getItem(i);
            final var output = unify(input);
            if (input.getItem() == output.getItem()) {
                continue;
            }
            container.setItem(i, output);
            ++unifiedItems;
        }
        return unifiedItems;
    }

    private int unifyInventory(final CommandContext<CommandSourceStack> context) {
        final var player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }

        final var unifiedItems = unifyInventory(player.getInventory());
        if (unifiedItems > 0) {
            player.sendSystemMessage(Component.translatable(String.format("message.%s.unified_items", MODID),
                unifiedItems));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int unifyPlayersIn(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final var sender = context.getSource().getPlayerOrException();
        sender.sendSystemMessage(Component.translatable(String.format("message.%s.performance_warning", MODID)));

        final var server = context.getSource().getServer();
        final var dimension = context.getArgument("dimension", ResourceLocation.class);
        final var dimensionKey = ResourceKey.create(Registries.DIMENSION, dimension);
        final var level = server.getLevel(dimensionKey);
        if (level == null) {
            return 0; // This should never happen
        }

        var unifiedItems = 0;
        for (final var player : level.getPlayers(player -> true)) {
            unifiedItems += unifyInventory(player.getInventory());
        }
        if (unifiedItems > 0) {
            sender.sendSystemMessage(Component.translatable(String.format("message.%s.unified_items_in", MODID),
                unifiedItems,
                dimension));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int unifyInventoriesInRange(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {

        final var sender = context.getSource().getPlayerOrException();
        final var level = sender.level();
        sender.sendSystemMessage(Component.translatable(String.format("message.%s.performance_warning", MODID)));

        final var pos = sender.getOnPos();
        final var range = (int) context.getArgument("rangeInChunks", Integer.class);

        var unifiedItems = 0;
        final var centerChunkPos = level.getChunk(pos).getPos();

        for (var zOffset = -range; zOffset < range; ++zOffset) {
            for (var xOffset = -range; xOffset < range; ++xOffset) {
                final var chunkX = centerChunkPos.x + xOffset;
                final var chunkZ = centerChunkPos.z + zOffset;
                final var chunk = level.getChunk(chunkX, chunkZ);
                for (final var blockEntityPos : chunk.getBlockEntitiesPos()) {
                    final var blockEntity = chunk.getExistingBlockEntity(blockEntityPos);
                    if (!(blockEntity instanceof Container container)) {
                        continue;
                    }

                    final var numberUnified = unifyInventory(container);
                    blockEntity.setChanged();
                    unifiedItems += numberUnified;
                    sender.sendSystemMessage(Component.translatable(String.format("message.%s.unified_items_at", MODID),
                        numberUnified,
                        blockEntityPos.toShortString()));
                }
            }
        }

        if (unifiedItems > 0) {
            sender.sendSystemMessage(Component.translatable(String.format("message.%s.unified_items", MODID),
                unifiedItems));
        }

        return Command.SINGLE_SUCCESS;
    }

    private void onRegisterCommands(final RegisterCommandsEvent event) {
        // @formatter:off
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal(MODID)
            .then(
                LiteralArgumentBuilder.<CommandSourceStack>literal("unify")
                    .then(
                        LiteralArgumentBuilder.<CommandSourceStack>literal("playersin").then(
                            RequiredArgumentBuilder.<CommandSourceStack, ResourceLocation>argument("dimension", DimensionArgument.dimension())
                                .executes(this::unifyPlayersIn)
                        )
                    )
                    .then(
                        LiteralArgumentBuilder.<CommandSourceStack>literal("all").then(
                            RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("rangeInChunks", IntegerArgumentType.integer())
                                .executes(this::unifyInventoriesInRange)
                        )
                    )
                    .executes(this::unifyInventory)
            ));
        // @formatter:on
    }
}
