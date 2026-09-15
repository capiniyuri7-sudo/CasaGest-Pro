# CasaGest Pro Android

Aplicativo Android nativo que hospeda a aplicacao web do CasaGest Pro em uma unica tela usando `WebView`.

O logo do aplicativo foi importado do Git em `app/src/main/res/drawable/app_logo.jpg` e configurado como icone principal e redondo no manifesto Android.

## Configuracao

O endereco padrao e `https://casa-gest-pro-site.vercel.app/`. Para gerar um APK apontando para outro ambiente:

```bash
gradle assembleDebug -PCASA_GEST_URL=https://seu-dominio.example.com
```

O projeto exige Android SDK com a plataforma 35 instalada. Defina `ANDROID_HOME` ou crie `local.properties` com `sdk.dir=/caminho/para/android-sdk` antes de compilar.

A versao minima suportada e Android 10 (API 29).

O WebView aceita apenas navegacao dentro da mesma origem configurada e o botao voltar percorre o historico da aplicacao.

O conteudo respeita as barras do sistema e recortes de tela por meio de `WindowInsets`. O navegador tambem usa o seletor nativo de arquivos para uploads, abre links externos com os aplicativos do dispositivo e suporta o gesto/botao nativo de voltar.

Quando a aplicacao web solicitar camera ou microfone, o Android exibe a permissao correspondente. O acesso so e concedido depois da aprovacao do usuario e apenas para a origem publica configurada do CasaGest Pro.

O tema visual acompanha automaticamente o tema do dispositivo. O shell Android, as barras do sistema, a barra de progresso e o WebView usam variantes claras e escuras; a alternancia ocorre quando o sistema recria a atividade.

## Notificacoes push

O cliente FCM esta integrado, mas o Firebase precisa ser configurado antes do envio real:

1. Crie ou selecione um projeto no Firebase Console e registre o app Android com o id `br.com.casagestpro`.
2. Baixe `google-services.json` e coloque-o em `app/google-services.json`.
3. Gere o APK novamente; o plugin Google Services sera habilitado automaticamente quando o arquivo estiver presente.

O site pode obter o token do dispositivo com `window.CasaGestNative.getPushToken()` e envia-lo ao backend. Mensagens FCM devem usar os campos `title`, `body` e, opcionalmente, `url` no payload `data`; a URL sera aberta no WebView somente se pertencer ao CasaGest Pro.
