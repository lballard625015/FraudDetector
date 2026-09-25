import { Client, IMessage } from '@stomp/stompjs'

export const connectLiveUpdates = (
  onAlert: (message: unknown) => void,
  onCase: (message: unknown) => void,
  onStatus: (connected: boolean) => void,
) => {
  const client = new Client({
    brokerURL: `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`,
    reconnectDelay: 5000,
    onConnect: () => {
      onStatus(true)
      client.subscribe('/topic/alerts', (message: IMessage) => onAlert(JSON.parse(message.body)))
      client.subscribe('/topic/cases', (message: IMessage) => onCase(JSON.parse(message.body)))
    },
    onDisconnect: () => onStatus(false),
    onStompError: () => onStatus(false),
  })
  client.activate()
  return () => void client.deactivate()
}
