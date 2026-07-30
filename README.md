# RotaCerta Delivery — Sistema de Despacho para Restaurantes

Sistema composto por **dois apps Android** (Restaurante + Motoboy) que se comunicam
**100% via rede Wi-Fi local**, sem depender de internet ou qualquer serviço em nuvem.
Construído reaproveitando ao máximo o app RotaCerta original (sistema de rotas,
navegação, tema e componentes de interface).

## Arquitetura

Projeto multi-módulo Gradle:

```
core/        -> Código reaproveitado do RotaCerta original + protocolo de comunicação
restaurant/  -> App do Restaurante (contém o servidor local embutido)
motoboy/     -> App do Motoboy (cliente, reaproveita o sistema de rotas)
```

### Por que o servidor fica dentro do app do Restaurante?

Não existe um "terceiro computador" na cozinha. O celular do restaurante *é* o
servidor: ele sobe um servidor WebSocket local (Ktor, porta 8087) assim que o
app abre, e anuncia isso na rede via **NSD/mDNS** (`_rotacerta._tcp.`). O app
do Motoboy escuta esse anúncio e conecta automaticamente — sem digitar IP.

```
[App Restaurante]                       [App Motoboy]
  - Cadastro do restaurante                - Login (ID cadastrado)
  - Cadastro de entregadores                - Conecta automaticamente (NSD)
  - Servidor WebSocket (:8087)  <--Wi-Fi-->  - Recebe chamadas
  - Fila de despacho (DispatchQueue)         - Aceita/recusa
  - Botão "Chamar Entregador"                - Usa o sistema de rotas já existente
  - Histórico                                  para navegar até o destino
```

### Fila de despacho (`core/protocol/DispatchQueue.kt`)

Pura lógica de domínio, sem I/O — fácil de testar isoladamente. Implementa:

- Fila FIFO de motoboys disponíveis
- Primeiro disponível recebe a chamada
- Timeout configurável (padrão 30s) → repassa automaticamente pro próximo
- Nunca duas chamadas simultâneas pro mesmo motoboy
- Ao concluir ou recusar, motoboy volta pro final da fila

### Protocolo (`core/protocol/Protocol.kt`)

Mensagens JSON trocadas via WebSocket em um envelope `{ type, data }`. Ver o
arquivo para a lista completa (REGISTER, CALL_OFFER, CALL_RESPONSE, etc).

## O que foi reaproveitado do RotaCerta original (sem reimplementar)

- `RouteOptimizer.kt`, `GeocodingService.kt`, `GpsLocationProvider.kt`
- Lógica de abrir navegação no Google Maps/Waze (`NavLauncher.kt`)
- Componente `DeliveryCard` (usado na tela do motoboy pra entrega ativa)
- Tema e cores (`Theme.kt`, `Color.kt`)

## Segurança / validação

O servidor só aceita a conexão de um motoboy se o ID dele estiver cadastrado
(e ativo) no Room do app do Restaurante — configurado em
`LocalServerService.kt` via `server.isMotoboyAllowed`.

## Como compilar

**Opção 1 — Android Studio**: abra a pasta raiz do projeto, deixe sincronizar
o Gradle (gera o wrapper automaticamente) e rode o módulo `restaurant` ou
`motoboy` conforme o caso.

**Opção 2 — GitHub Actions**: o workflow em `.github/workflows/android-build.yml`
compila os dois APKs (debug) sem precisar de Android Studio. Rode manualmente
em Actions → "Build Android APKs" → Run workflow, ou faça push na branch `main`.

## Limitações desta versão (v1 — modo 100% offline/local)

- Restaurante e motoboys precisam estar na **mesma rede Wi-Fi**.
- Se o app do Restaurante fechar totalmente, o servidor cai — o foreground
  service tenta evitar isso, mas o celular do restaurante precisa ficar ligado
  durante o expediente.
- Preparado para, no futuro, adicionar um "modo online" (trocar o transporte
  local por um servidor na nuvem) sem precisar reescrever a fila de despacho
  nem a UI — só a camada de rede (`LocalDispatchServer`/`MotoboyWebSocketClient`)
  precisaria de uma variante.
