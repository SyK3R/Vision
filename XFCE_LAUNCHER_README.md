{% raw %}
# XFCE Auto-Launcher para Termux:X11

Uma solução completa para automatizar o lançamento do ambiente de desktop XFCE (ou KDE/GNOME) diretamente no Termux, rodando sobre o servidor X11.

## 🎯 Objetivo

Este projeto estende o Termux:X11 fornecendo:

- **Inicialização Automática**: Inicia XFCE automaticamente ao abrir o aplicativo
- **Gerenciamento de Processo**: Monitora e reinicia a sessão se ela crashar
- **Múltiplos Desktops**: Suporte para XFCE, KDE e GNOME
- **Interface Integrada**: Botões para controlar a sessão direto do app
- **Foreground Service**: Mantém a sessão viva mesmo quando o app está em background

## 📋 Arquitetura

### Componentes Principais

```
MainActivity (UI Principal)
    ↓
XFCELauncherService (Foreground Service)
    ↓
XFCEManager (Gerenciador Central)
    ├─ ProcessManager (Execução de comandos)
    ├─ Inicializa termux-x11
    └─ Inicia xfce4-session
```

### Fluxo de Inicialização

```
1. Usuário abre MainActivity
2. MainActivity chama XFCELauncherService.startService()
3. Service inicia XFCEManager.startXFCESession()
4. XFCEManager:
   - Executa: termux-x11 :1
   - Aguarda socket X11 estar pronto
   - Executa: DISPLAY=:1 dbus-launch xfce4-session
   - Monitora processo (restart automático se crashar)
```

## 📦 Arquivos Adicionados

### 1. **XFCEManager.java**
Controlador central do XFCE. Responsabilidades:
- Iniciar e parar servidor X11
- Gerenciar sessão XFCE/KDE/GNOME
- Monitorar e reiniciar processos
- Thread-safe com AtomicBoolean

**Métodos principais:**
```java
startXFCESession()           // Inicia tudo
stopXFCESession()            // Para tudo
setDesktopEnvironment(de)    // Muda DE (xfce/kde/gnome)
isSessionRunning()           // Verifica status
```

### 2. **ProcessManager.java**
Executor de comandos shell com monitoramento. Características:
- Executa comandos shell do Android
- Captura stdout/stderr em tempo real
- Gerencia ciclo de vida do processo
- Pattern Observer para callbacks

**Listener Interface:**
```java
interface ProcessListener {
    onProcessStarted()
    onProcessOutput(String line)
    onProcessError(String line)
    onProcessFinished(int exitCode)
    onProcessCrashed(Exception e)
}
```

### 3. **XFCELauncherService.java**
Serviço foreground que mantém XFCE rodando. Características:
- Notification para manter processo vivo
- Inicia com ação "START_XFCE"
- Para com ação "STOP_XFCE"
- Acesso aos managers via LocalBinder

**Uso:**
```java
XFCELauncherService.startService(context, "xfce");
XFCELauncherService.stopService(context);
```

### 4. **Recursos (Resources)**
- `xfce_strings.xml`: Strings UI, notificações, mensagens de erro

### 5. **AndroidManifest.xml** (Atualizado)
- Declaração do serviço
- Permissões necessárias:
  - `FOREGROUND_SERVICE`
  - `INTERNET`
  - `WRITE_SECURE_SETTINGS`
  - `POST_NOTIFICATIONS`

## 🚀 Como Usar

### Instalação de Dependências

Dentro do Termux:
```bash
# Instalar X11 server
pkg install termux-x11

# Instalar desktop environment
pkg install xfce4 dbus

# Ou para KDE:
pkg install kde-plasma dbus

# Ou para GNOME:
pkg install gnome-session dbus
```

### Uso no Código

```java
// Iniciar XFCE automaticamente
XFCELauncherService.startService(this, "xfce");

// Parar XFCE
XFCELauncherService.stopService(this);

// Acessar gerenciador
XFCEManager manager = XFCEManager.getInstance(this);
if (manager.isSessionRunning()) {
    Log.d("XFCE", "Rodando: " + manager.getDesktopEnvironment());
}
```

### Integração com MainActivity

No `onCreate()` ou `onResume()`:
```java
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // ... código existente ...
    
    // Iniciar XFCE se preferência estiver ativa
    if (prefs.autoStartXFCE.get()) {
        XFCELauncherService.startService(this, "xfce");
    }
}
```

Adicionar botões na UI:
```java
findViewById(R.id.start_xfce_button).setOnClickListener(v -> 
    XFCELauncherService.startService(MainActivity.this, "xfce"));

findViewById(R.id.stop_xfce_button).setOnClickListener(v -> 
    XFCELauncherService.stopService(MainActivity.this));
```

## 🛠️ Configuração Avançada

### Mudar Desktop Environment em Runtime

```java
XFCEManager manager = XFCEManager.getInstance(this);
manager.setDesktopEnvironment("kde");  // Muda para KDE
manager.stopXFCESession();
manager.startXFCESession();
```

### Monitorar Processos

```java
ProcessManager pm = new ProcessManager();
pm.addListener(new ProcessManager.ProcessListener() {
    @Override
    public void onProcessStarted() {
        Log.d("Process", "Iniciado");
    }
    
    @Override
    public void onProcessOutput(String line) {
        Log.d("Process", "Output: " + line);
    }
    
    @Override
    public void onProcessFinished(int exitCode) {
        Log.d("Process", "Finalizado com: " + exitCode);
    }
});

pm.executeCommand("your-command-here");
```

### Retry Automático

O XFCEManager tenta reiniciar até **5 vezes** com delay de **3 segundos** entre tentativas:

```java
// Configurar no XFCEManager se necessário
// (atualmente hardcoded, mas pode ser exposto)
private int restartDelayMs = 3000;
private static final int MAX_RESTART_ATTEMPTS = 5;
```

## 🔧 Troubleshooting

### XFCE não inicia
1. Verificar se `termux-x11` está instalado: `which termux-x11`
2. Verificar se `xfce4-session` está instalado: `which xfce4-session`
3. Verificar logs: `logcat | grep XFCEManager`

### Processo X11 morre rapidamente
1. Verificar permissões do Termux
2. Verificar espaço em disco disponível
3. Ver logs de erro do dbus: `journalctl -u dbus`

### Sessão não reinicia
1. Verificar se restart attempts chegou ao máximo
2. Verificar logs de crash em `dmesg`
3. Tentar aumentar `MAX_RESTART_ATTEMPTS`

## 📝 Logs Úteis

```bash
# Ver logs da aplicação
adb logcat | grep -E "(XFCEManager|ProcessManager|XFCELauncher)"

# Ver logs do sistema
adb shell logcat | grep termux-x11

# Ver logs do dbus
dbus-monitor --system
```

## ✨ Recursos Futuros

- [ ] Interface gráfica completa com seleção de DE
- [ ] Configurações persistentes (prefs)
- [ ] Suporte a Cinnamon, MATE
- [ ] Detecção automática de DEs instaladas
- [ ] Modo headless (sem UI)
- [ ] VNC integration para acesso remoto
- [ ] Performance profiling
- [ ] Suporte a múltiplos displays

## 🤝 Contribuindo

1. Crie uma branch: `git checkout -b feature/meu-recurso`
2. Faça commit: `git commit -am 'Adiciona meu recurso'`
3. Push para a branch: `git push origin feature/meu-recurso`
4. Abra um Pull Request

## 📄 Licença

GPLv3 - mesmo que Termux

## 🔗 Referências

- [Termux:X11 GitHub](https://github.com/termux/termux-x11)
- [Termux Packages](https://github.com/termux/termux-packages)
- [XFCE Wiki](https://wiki.xfce.org/)

## 📧 Suporte

Para issues, dúvidas ou sugestões:
1. Verificar [Issues](https://github.com/SyK3R/Vision/issues)
2. Criar novo issue com logs
3. Descrever comportamento esperado vs atual

---

**Status do Projeto**: 🟡 Em Desenvolvimento  
**Última Atualização**: Setembro 2026  
**Versão**: 0.1.0 (Beta)
{% endraw %}
