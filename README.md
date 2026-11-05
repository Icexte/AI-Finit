# AI Finit 2.0 — Android

Projeto Kotlin + Jetpack Compose para Android. Inclui chat conectado a provedor externo compatível com OpenAI Chat Completions.

## Novidades da versão 2.0
- Personalidades: Equilibrada, Amiga, Romântica, Direta e Criativa.
- Ditado por voz usando o reconhecedor de fala disponível no Android.
- Leitura das respostas em voz alta com TextToSpeech.
- Histórico local persistente (até 80 mensagens).
- Botão para limpar a conversa.
- Configurações de endpoint, modelo, chave, personalidade e voz.

## Limitações e transparência
- Não existe modelo próprio incluído. O usuário deve configurar um provedor com plano gratuito ou infraestrutura própria.
- Plano gratuito pode ter limites, exigir chave, mudar ou deixar de existir. O provedor decide seus filtros e disponibilidade.
- A confirmação de 18+ é declaratória, não verifica identidade nem idade legalmente.
- O histórico fica no armazenamento privado do app; esta versão não sincroniza entre aparelhos nem oferece backup/exportação.
- A chave é salva em SharedPreferences privadas, sem Android Keystore nesta versão. Não é adequado para distribuição pública com credenciais embutidas.
- Voz depende dos recursos e idiomas instalados no Android. O reconhecimento pode enviar áudio ao serviço de fala do aparelho.
- As mensagens são enviadas ao endpoint configurado. Leia a política de privacidade do provedor.
- A IA não remove as regras ou limitações do provedor.

## Compilar pelo GitHub usando celular
1. Baixe e extraia o ZIP.
2. Crie um repositório GitHub chamado `AI-Finit`.
3. Envie o conteúdo da pasta para a raiz do repositório (incluindo `.github/workflows/build.yml`).
4. Abra **Actions** e permita workflows, se solicitado.
5. Execute **Build AI Finit APK** ou aguarde o push.
6. Abra a execução concluída e baixe o artefato `AI-Finit-v2-debug-apk`.
7. Extraia o artefato e instale `app-debug.apk`. O Android pode pedir autorização para instalar apps dessa origem.

## Configurar o provedor
Na AI Finit, abra Configurações e preencha:
- URL HTTPS completa do endpoint `/v1/chat/completions`;
- nome exato do modelo;
- chave de API, caso exigida.

Esses dados são fornecidos pela documentação do provedor escolhido. Não há endpoint universal gratuito. Nunca coloque chaves no código ou no repositório público.

## Requisitos de compilação
- JDK 17
- Gradle 8.9
- Android SDK Platform 35
- Android Gradle Plugin 8.7.3
