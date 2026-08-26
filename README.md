# XCoder IDE

<p align="center">
  <strong>Um IDE Android completo, estável e multilingue para dispositivos móveis</strong><br>
  <sub>Editor de código, terminal, projetos, Git, compilação e ferramentas de produtividade num único lugar.</sub>
</p>

<p align="center">
  <a href="https://github.com/carsaimz/xcoder-ide/actions/workflows/ci.yml"><img src="https://github.com/carsaimz/xcoder-ide/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/carsaimz/xcoder-ide" alt="Licença"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-1.9%2B-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=android&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://developer.android.com/about/versions/14"><img src="https://img.shields.io/badge/Android%20SDK-34-3DDC84?logo=android&logoColor=white" alt="Android SDK 34"></a>
</p>

> **Estado:** em desenvolvimento activo. Cada alteração publicada deve passar a compilação, os testes unitários, o lint e a verificação final de CI antes de ser considerada concluída.

## Funcionalidades

O XCoder IDE combina uma experiência de edição orientada para Android com ferramentas que normalmente exigem um computador. Inclui o [sora-editor](https://github.com/Rosemoe/sora-editor), com realce TextMate para mais de 30 linguagens, dobragem de código, auto-completação, minimapa, guias de indentação, correspondência de chavetas, breadcrumbs e pesquisa/substituição com expressões regulares.

A aplicação também integra um terminal baseado no [Termux terminal-emulator](https://github.com/termux/termux-app), árvore de ficheiros com carregamento preguiçoso, Java Language Server via LSP4J, assistente de IA multi-fornecedor, editor visual por blocos, JGit, motor de compilação Gradle, editor de APK, sistema de extensões, formatadores de Kotlin/Java/JSON/XML/HTML/CSS, pesquisa transversal e marcadores.

O arranque utiliza o AndroidX SplashScreen de forma compatível com o tema da aplicação, evita abrir definições de armazenamento de forma invasiva no Android 11 ou posterior, trata permissões SAF rejeitadas por fornecedores externos e mantém inicializações opcionais fora do caminho crítico da primeira UI.

## Idiomas

O idioma predefinido é **português de Portugal (pt-PT)**. A escolha é persistida no dispositivo e pode ser alterada em **Definições → Idioma**. A interface disponibiliza os seguintes locales:

| Locale | Idioma | Estado |
|---|---|---|
| `pt-PT` | Português (Portugal) | Predefinido e revisto |
| `en` | English | Revisto |
| `es` | Español | Revisto |
| `fr` | Français | Revisto |
| `de` | Deutsch | Disponível |
| `it` | Italiano | Disponível |
| `ru` | Русский | Disponível |
| `zh-CN` | 简体中文 | Disponível |
| `ja` | 日本語 | Disponível |
| `ko` | 한국어 | Disponível |
| `ar` | العربية | Disponível, incluindo suporte RTL do sistema |
| `pt-BR` | Português (Brasil) | Disponível |

As traduções vivem nos recursos Android `values-*`, pelo que novas telas podem adoptar gradualmente o catálogo existente sem duplicar lógica de apresentação. Contribuições de tradução são bem-vindas, sobretudo para mensagens específicas das ferramentas.

## Identidade visual

O launcher usa um ícone adaptativo com um **X geométrico** em primeiro plano, aplicado sobre o fundo de marca roxo/azul. O símbolo foi desenhado como vector drawable para conservar nitidez em diferentes densidades e formatos de launcher, incluindo variantes redondas.

## Stack técnica

| Componente | Tecnologia |
|---|---|
| Linguagem | Kotlin 1.9+ |
| Interface | Jetpack Compose + Material 3 |
| Injecção de dependências | Dagger Hilt |
| Min SDK | 21 (Android 5.0) |
| Target/Compile SDK | 34 |
| JVM | 17 |
| Android Gradle Plugin | 8.2.0 |
| Gradle | 8.5 |
| Persistência | DataStore Preferences e preferências de locale |

## Módulos

```text
xcoder-ide/
├── app/                          # Aplicação principal, navegação e UI
├── core/
│   ├── file-manager/             # Operações de ficheiros e SAF
│   ├── terminal/                 # Termux terminal-emulator
│   ├── git/                      # Integração JGit
│   └── settings/                 # Preferências e DataStore
├── editor/                       # Wrapper do Rosemoe sora-editor
├── visual-editor/                # Construtor visual por blocos
├── build-engine/                 # Sistema de compilação Gradle
├── ai-copilot/                   # Assistente de código multi-fornecedor
├── search-in-project/            # Pesquisa transversal
├── code-formatter/               # Formatação de código
├── bookmarks/                    # Gestão de marcadores
├── apk-editor/                   # Editor APK/Smali/Dex
├── remote-filesystem/            # Cliente FTP/SFTP
├── lsp-java/                     # Java Language Server (jdtls)
└── plugin-system/                # API e carregador de extensões
```

## CI/CD

O workflow [CI](.github/workflows/ci.yml) usa Gradle 8.5 através de `gradle/actions/setup-gradle@v4` e mantém todos os controlos bloqueantes. Uma alteração só está pronta quando **Resolve Dependencies**, **Build All Modules**, **Unit Tests**, **Lint & Code Quality** e **CI Status** aparecem como concluídos com sucesso no GitHub Actions.

Existem ainda workflows para build de debug, release assinado, tags/changelog, actualização de changelog, verificação manual de Gradle e Dependabot. O clone não depende de um wrapper Gradle local; em ambiente de desenvolvimento, use Gradle 8.5 ou uma distribuição compatível.

## Compilar e testar

```bash
gradle assembleDebug          # APK de debug
gradle assembleRelease        # Release, com configuração de assinatura
gradle testDebugUnitTest     # Testes unitários
gradle lintDebug              # Lint Android e qualidade de código
```

Antes de submeter uma alteração, execute também `git diff --check` e confirme a execução completa do workflow no separador **Actions** do repositório.

## Créditos e licenças

O projecto aproveita bibliotecas, APIs e padrões de projectos open source, respeitando as respectivas licenças. Agradecimentos especiais a [Rosemoe](https://github.com/Rosemoe), [Termux](https://github.com/termux), [AndroidIDE](https://github.com/AndroidIDE/AndroidIDE), [Sketchware-IA](https://github.com/FabioSilva11/Sketchware-IA), [Dalvikus](https://github.com/loerting/dalvikus), [AndroidTreeView](https://github.com/bmelnychuk/AndroidTreeView) e [Eclipse JDT Language Server](https://github.com/eclipse-jdtls/eclipse.jdt.ls). Consulte [LICENSE](LICENSE) para os termos do XCoder IDE.

## Contribuir

Aceitam-se correcções de estabilidade, melhorias de acessibilidade, traduções e integrações de ferramentas. Mantenha alterações pequenas e verificáveis, documente qualquer código reutilizado com a licença correspondente e não abra uma pull request enquanto o workflow CI não estiver completamente verde.
