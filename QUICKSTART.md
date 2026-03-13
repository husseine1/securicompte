# 🚀 Démarrage Rapide - Securicompte

## ⚡ En 5 Minutes

### Prérequis Installés?
- ✅ Java 17+
- ✅ Maven 3.8+
- ✅ PostgreSQL 15+ (en cours d'exécution)

Si non → Allez voir **SETUP.md**

### Lancer l'Application

**Linux/macOS:**
```bash
chmod +x start.sh
./start.sh
```

**Windows:**
```batch
start.bat
```

✅ Attendez que le script affiche: `Started SecuricompteApplication`

---

## 🌐 Accès

📍 URL: **http://localhost:8080**

👤 **Identifiants:**
- Admin: `admin` / `Admin@2024`
- Agent: `agent1` / `Agent@2024`

---

## 🆘 Le script ne marche pas?

### Linux/macOS: "Permission denied"
```bash
chmod +x start.sh
./start.sh
```

### Windows: Rien ne se passe
- Clic droit sur `start.bat` → "Run as Administrator"

### PostgreSQL pas trouvé
```bash
# Linux
sudo systemctl start postgresql

# macOS
brew services start postgresql@15
```

### Java/Maven pas trouvé
Voir **SETUP.md** → section Installation des prérequis

---

## 📖 Pour Plus d'Infos

- **README.md** - Vue d'ensemble du projet
- **SETUP.md** - Installation détaillée
- **COMMANDS.md** - Commandes courantes
- **MODIFICATIONS.md** - Ce qui a changé depuis Docker

---

## 🔄 Pendant le Développement

### Recompiler après modification du code
```bash
mvn clean package -DskipTests
```

### Arrêter l'application
```
Appuyez sur Ctrl+C
```

### Voir les logs
- Ils s'affichent directement dans le terminal
- Cherchez les lignes `ERROR` ou `WARN`

---

## ✨ C'est Tout!

Votre application est prête à l'emploi. Explorez le dashboard et les fonctionnalités!

👨‍💻 Besoin d'aide? Consultez **SETUP.md** ou **COMMANDS.md**
