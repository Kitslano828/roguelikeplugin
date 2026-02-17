package TomDang.example.velocityOrchestrator.party;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PartyCommand implements SimpleCommand {
    private final ProxyServer proxy;
    private final PartyManager parties;

    public PartyCommand(ProxyServer proxy, PartyManager parties) {
        this.proxy = proxy;
        this.parties = parties;
    }

    @Override
    public void execute(Invocation inv) {
        if (!(inv.source() instanceof Player player)) {
            inv.source().sendMessage(Component.text("Players only."));
            return;
        }

        String[] args = inv.arguments();
        if (args.length == 0) {
            showInfo(player);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "create" -> {
                Party p = parties.createParty(player.getUniqueId());
                player.sendMessage(Component.text("Party created. ID=" + p.partyId()));
                showInfo(player);
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /party invite <player>"));
                    return;
                }
                Optional<Player> targetOpt = proxy.getPlayer(args[1]);
                if (targetOpt.isEmpty()) {
                    player.sendMessage(Component.text("Player not found online: " + args[1]));
                    return;
                }
                Player target = targetOpt.get();

                boolean ok = parties.invite(player.getUniqueId(), target.getUniqueId());
                if (!ok) {
                    player.sendMessage(Component.text("Only the party leader can invite."));
                    return;
                }
                player.sendMessage(Component.text("Invited " + target.getUsername() + "."));
                target.sendMessage(Component.text(player.getUsername() + " invited you to a party. Type /party accept"));
            }
            case "accept" -> {
                PartyManager.AcceptResult res = parties.accept(player.getUniqueId());
                switch (res) {
                    case NO_INVITE -> player.sendMessage(Component.text("No pending invite."));
                    case EXPIRED -> player.sendMessage(Component.text("Invite expired."));
                    case PARTY_MISSING -> player.sendMessage(Component.text("Party no longer exists."));
                    case JOINED -> {
                        player.sendMessage(Component.text("Joined party."));
                        showInfo(player);
                    }
                }
            }
            case "leave" -> {
                boolean left = parties.leave(player.getUniqueId());
                player.sendMessage(Component.text(left ? "Left party." : "You are not in a party."));
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /party kick <player>"));
                    return;
                }
                Optional<Player> targetOpt = proxy.getPlayer(args[1]);
                if (targetOpt.isEmpty()) {
                    player.sendMessage(Component.text("Player not found online: " + args[1]));
                    return;
                }
                Player target = targetOpt.get();

                boolean ok = parties.kick(player.getUniqueId(), target.getUniqueId());
                player.sendMessage(Component.text(ok ? "Kicked " + target.getUsername() : "Kick failed (leader only / not in your party)."));
                if (ok) {
                    target.sendMessage(Component.text("You were kicked from the party."));
                }
            }
            case "disband" -> {
                parties.getPartyOf(player.getUniqueId()).ifPresentOrElse(p -> {
                    if (!p.isLeader(player.getUniqueId())) {
                        player.sendMessage(Component.text("Only the leader can disband."));
                        return;
                    }
                    parties.disband(p.partyId());
                    player.sendMessage(Component.text("Party disbanded."));
                }, () -> player.sendMessage(Component.text("You are not in a party.")));
            }
            case "info" -> showInfo(player);
            default -> player.sendMessage(Component.text("Party commands: create, invite, accept, leave, kick, disband, info"));
        }
    }

    private void showInfo(Player player) {
        parties.getPartyOf(player.getUniqueId()).ifPresentOrElse(p -> {
            player.sendMessage(Component.text("Party ID: " + p.partyId()));
            player.sendMessage(Component.text("Leader: " + nameOf(p.leader())));
            player.sendMessage(Component.text("Members:"));
            for (UUID m : p.membersView()) {
                player.sendMessage(Component.text(" - " + nameOf(m)));
            }
        }, () -> player.sendMessage(Component.text("You are not in a party. Use /party create")));
    }

    private String nameOf(UUID uuid) {
        return proxy.getPlayer(uuid).map(Player::getUsername).orElse(uuid.toString());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation inv) {
        if (!(inv.source() instanceof Player)) return CompletableFuture.completedFuture(List.of());
        String[] args = inv.arguments();

        if (args.length == 0) {
            return CompletableFuture.completedFuture(List.of("create","invite","accept","leave","kick","disband","info"));
        }
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> subs = new ArrayList<>(List.of("create","invite","accept","leave","kick","disband","info"));
            subs.removeIf(s -> !s.startsWith(p));
            return CompletableFuture.completedFuture(subs);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            String p = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player pl : proxy.getAllPlayers()) {
                if (pl.getUsername().toLowerCase(Locale.ROOT).startsWith(p)) names.add(pl.getUsername());
            }
            return CompletableFuture.completedFuture(names);
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
