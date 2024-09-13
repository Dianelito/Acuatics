package ac.dianelito.acuatics;
import java.util.Arrays;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class Acuatics extends JavaPlugin {

    private Connection connection;
    private JDA jda;
    private String channelId;
    private String adminRoleId;

    @Override
    public void onEnable() {
        // Cargar la configuración
        saveDefaultConfig();

        // Conectar a la base de datos MySQL
        connectToDatabase();

        // Conectar al bot de Discord
        setupDiscordBot();
    }

    @Override
    public void onDisable() {
        disconnectDatabase();
        if (jda != null) {
            jda.shutdown();
        }
    }

    // Comandos de Minecraft
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando solo puede ser usado en el juego.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (label.equalsIgnoreCase("points")) {
            int points = getStaffPoints(playerUUID.toString());
            player.sendMessage("Tienes " + points + " Staff Points.");
            return true;
        }

        if (label.equalsIgnoreCase("strikes")) {
            int strikes = getStaffStrikes(playerUUID.toString());
            player.sendMessage("Tienes " + strikes + " Strikes.");
            return true;
        }

        return false;
    }

    // Conectar a la base de datos MySQL
    private void connectToDatabase() {
        String host = getConfig().getString("mysql.host");
        int port = getConfig().getInt("mysql.port");
        String database = getConfig().getString("mysql.database");
        String username = getConfig().getString("mysql.username");
        String password = getConfig().getString("mysql.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;

        try {
            connection = DriverManager.getConnection(url, username, password);
            getLogger().info("Conectado a la base de datos MySQL.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Desconectar de la base de datos MySQL
    private void disconnectDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                getLogger().info("Conexión a la base de datos cerrada.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Obtener Staff Points
    private int getStaffPoints(String staffId) {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT points FROM staff_points WHERE staff_id = ?");
            statement.setString(1, staffId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getInt("points");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    // Obtener Strikes
    private int getStaffStrikes(String staffId) {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT strikes FROM staff_strikes WHERE staff_id = ?");
            statement.setString(1, staffId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getInt("strikes");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    // Configuración del bot de Discord
    private void setupDiscordBot() {
        try {
            String token = getConfig().getString("discord.token");
            channelId = getConfig().getString("discord.channel_id");
            adminRoleId = getConfig().getString("discord.admin_role_id");

            jda = JDABuilder.createDefault(token).build();
            jda.addEventListener(new DiscordListener());

            getLogger().info("Bot de Discord conectado.");
        } catch (LoginException e) {
            e.printStackTrace();
        }
    }

    // Clase interna para manejar eventos del bot de Discord
    private class DiscordListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            // Comprobar que el mensaje viene del canal correcto
            if (!event.getChannel().getId().equals(channelId)) {
                return;
            }

            // Comprobar que el autor tiene el rol adecuado
            Role adminRole = event.getGuild().getRoleById(adminRoleId);
            if (!event.getMember().getRoles().contains(adminRole)) {
                event.getChannel().sendMessage("No tienes permisos para usar este comando.").queue();
                return;
            }

            // Procesar comandos para añadir o quitar puntos/strikes
            String[] args = event.getMessage().getContentRaw().split(" ");
            if (args[0].equalsIgnoreCase("!addpoints")) {
                handleAddPoints(event, args);
            } else if (args[0].equalsIgnoreCase("!removepoints")) {
                handleRemovePoints(event, args);
            } else if (args[0].equalsIgnoreCase("!addstrike")) {
                handleAddStrike(event, args);
            } else if (args[0].equalsIgnoreCase("!removestrike")) {
                handleRemoveStrike(event, args);
            }
        }

        private void handleAddPoints(MessageReceivedEvent event, String[] args) {
            String staffId = args[1];
            int amount = Integer.parseInt(args[2]);
            String reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

            updateStaffPoints(staffId, amount);

            int totalPoints = getStaffPoints(staffId);

            String message = getConfig().getString("messages.points_added")
                    .replace("{staff}", staffId)
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{reason}", reason)
                    .replace("{total}", String.valueOf(totalPoints));

            event.getChannel().sendMessage(message).queue();
        }

        private void handleRemovePoints(MessageReceivedEvent event, String[] args) {
            // Similar lógica a handleAddPoints
        }

        private void handleAddStrike(MessageReceivedEvent event, String[] args) {
            // Similar lógica a handleAddPoints
        }

        private void handleRemoveStrike(MessageReceivedEvent event, String[] args) {
            // Similar lógica a handleAddPoints
        }

        private void updateStaffPoints(String staffId, int amount) {
            try {
                PreparedStatement statement = connection.prepareStatement("UPDATE staff_points SET points = points + ? WHERE staff_id = ?");
                statement.setInt(1, amount);
                statement.setString(2, staffId);
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
