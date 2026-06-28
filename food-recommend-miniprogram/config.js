// Global miniprogram API config.
// Put machine-specific settings in config.local.js; that file is gitignored.
let localConfig = {}
try {
  localConfig = require('./config.local')
} catch (e) {
  localConfig = {}
}

const PORT = localConfig.PORT || 8080
const LAN_IP = localConfig.LAN_IP || '127.0.0.1'

let isDevTools = false
try {
  if (wx.getDeviceInfo) {
    isDevTools = wx.getDeviceInfo().platform === 'devtools'
  } else {
    isDevTools = wx.getSystemInfoSync().platform === 'devtools'
  }
} catch (e) {
  isDevTools = false
}

const BASE_URL = localConfig.BASE_URL || (
  isDevTools ? `http://127.0.0.1:${PORT}` : `http://${LAN_IP}:${PORT}`
)

module.exports = {
  BASE_URL,
  LAN_IP,
  PORT
}
