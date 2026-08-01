package com.biomebounty.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * 把 Bukkit CommandExecutor 桥接到 Paper Brigadier 命令树
 */
public final class CommandBridge {

    private CommandBridge() {}

    /**
     * 构建 Brigadier 命令节点，委派给已有的 Bukkit CommandExecutor
     */
    public static LiteralCommandNode<CommandSourceStack> wrap(String name, CommandExecutor executor) {
        return Commands.literal(name)
                .executes(ctx -> exec(executor, ctx.getSource(), ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> exec(executor, ctx.getSource(),
                                StringArgumentType.getString(ctx, "args"))))
                .build();
    }

    private static int exec(CommandExecutor executor, CommandSourceStack src, String raw) {
        CommandSender sender = src.getSender();
        String[] args = raw.isEmpty() ? new String[0] : raw.split("\\s+");
        try {
            // CommandExecutor.onCommand 不需要 Command 和 label 参数（我们的实现不使用它们）
            boolean success = executor.onCommand(sender, null, null, args);
            return success ? 1 : 0;
        } catch (Exception e) {
            sender.sendMessage("§c命令执行出错: " + e.getMessage());
            return 0;
        }
    }
}
