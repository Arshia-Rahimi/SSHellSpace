import { Terminal } from '@xterm/xterm';
import { FitAddon } from '@xterm/addon-fit';

const term = new Terminal({ cursorBlink: true });
const fitAddon = new FitAddon();
term.loadAddon(fitAddon);
term.open(document.getElementById('terminal'));
fitAddon.fit();

// Outbound: keystrokes -> Kotlin
term.onData((data) => {
    window.kmpJsBridge.callNative("ptyInput", data, () => {});
});

// Inbound: Kotlin calls this global to push PTY output into xterm
window.writeToTerminal = (data) => term.write(data);

window.addEventListener('resize', () => fitAddon.fit());