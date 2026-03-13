# UPDATE

## 2026-03-10 ~ 2026-03-11 会话记录

### 修改日期
- 2026-03-10 ~ 2026-03-11

### 修改内容
- 后端目录规则判断
  - 在 `src/main/java/io/legado/app/help/DefaultData.kt` 新增 `isBuiltinTxtTocRule`。
  - 在 `src/main/java/io/legado/app/model/localBook/TextFile.kt` 中将 `strictCustomRule` 改为只对真正的内置 TXT 目录规则放宽判断，不再把外部/自定义规则当成内置规则处理。
- 回归测试
  - 在 `src/test/kotlin/io/legado/app/model/localBook/TextFileTest.kt` 补充 issue #659 相关回归测试。
  - 新增“外部自定义目录规则仍然使用严格卷名判断”的测试，防止后续再次回退。
- 本地测试链路
  - 在本机安装 `JDK 11` 到 `C:\Users\renas\.jdks\jdk-11.0.30+7`。
  - 持久化用户环境变量 `JAVA11_HOME`。
  - 新增 `scripts/gradle-jdk11.ps1`，用于固定用 JDK 11 跑 Gradle，并避免旧 daemon 串用错误 Java 版本。
  - 在 `README.md` 中补充本地测试说明。
- 前端阅读页修正
  - 在 `web/src/views/Reader.vue` 中保留并整理手机模式下的左右安全区间距处理。
  - 新增 `getSlidePageWidth()`，将手机左右滑动分页从硬编码的 `windowSize.width - 16` 改为按真实阅读页宽计算。
  - 同步修正分页总数、翻页动画、触摸滑动收尾、段落跳转定位等逻辑，避免页数越往后偏移越大。
- 构建与运行验证
  - 重新构建并启动源码版 Docker 容器。
  - 验证 `http://localhost:18080/` 返回 `HTTP 200`。

### 整体思路
- 先按 issue #659 的描述拆成两条链路排查：
  - 后端：自定义 TXT 目录规则是否改变了章节结构数据。
  - 前端：手机模式下阅读区宽度、分页位移、顶部占位是否使用了错误的固定值。
- 对后端先补回归测试，再修逻辑，避免继续靠肉眼猜测。
- 对前端不再只补 CSS，而是追到真正控制分页和位移的 JS 逻辑，统一改为使用真实 DOM 宽度。
- 同时补齐本地测试环境，确保后续能稳定复测，而不是每次都被 Java/Gradle 版本问题阻塞。

### 为啥要这样修改
- 自定义目录规则不应该被当成内置规则处理。
  - 否则章节 `isVolume` 判定会发生偏差，进而影响阅读页的渲染分支和目录行为。
- 手机左右滑动模式不应该依赖固定的 `windowSize.width - 16`。
  - 当前页面真实宽度同时受 `100vw`、padding、安全区、顶部/底部结构影响，固定值会导致第 2 页以后累计偏移。
  - 这个偏移在视觉上会表现成“左右边距异常”、“上一页内容挤进来”或者“正文区域被压缩”。
- 本地测试必须固定到 JDK 11。
  - 该项目在宿主机上同时受 Gradle 6.1.1、Groovy、JavaFX 插件影响，JDK 8 / 21 都会导致测试链路不稳定或直接失败。

### 结果与当前状态
- 已完成
  - 后端自定义目录规则判断修正。
  - issue #659 相关回归测试补充完成。
  - 本地 JDK 11 测试链路打通。
  - `TextFileTest` 和完整 `test` 任务均已在本机成功执行。
  - Docker 服务已重新构建并启动。
- 仍需继续确认
  - 这次会话最后追加的是前端手机分页宽度修正。
  - 该修正已经构建进容器，但是否完全覆盖用户截图中的最终表现，仍需要在实际浏览器里继续复测。
  - 由于项目启用了 PWA / service worker，前端复测前需要注意浏览器缓存可能导致旧脚本继续生效。

### 后续查看建议
- 优先查看本次涉及的核心文件：
  - `src/main/java/io/legado/app/help/DefaultData.kt`
  - `src/main/java/io/legado/app/model/localBook/TextFile.kt`
  - `src/test/kotlin/io/legado/app/model/localBook/TextFileTest.kt`
  - `web/src/views/Reader.vue`
  - `scripts/gradle-jdk11.ps1`
  - `README.md`
- 本地复测命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\gradle-jdk11.ps1 test
```

- 定向验证 issue #659 相关测试：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\gradle-jdk11.ps1 test --tests io.legado.app.model.localBook.TextFileTest
```
## 2026-03-11 会话追加记录

### 修改日期
- 2026-03-11

### 本轮新增问题定位
- 阅读页整页刷新后，前端在 `readingBook.bookUrl` 还没完全恢复时，就先发出了只带 `{"refresh":0}` 的 `/reader3/getChapterList` 请求。
- 后端 `src/main/java/com/htmake/reader/api/controller/BookController.kt` 里对 `context.bodyAsJson` 的读取不够安全，这种半截请求会直接触发 `java.lang.NullPointerException`。
- 前端 `web/src/views/Reader.vue` 在初始化失败时又停留在 `show = false`，所以页面表现为“提示一下异常，然后正文全空白，必须退回书架再点一次”。
- 源码版 Docker 重新部署时，容器内 `yarn` 拉包不稳定，因此还补了一次源码镜像构建链路。

### 本轮修改思路
- 先从日志反推，而不是继续只看前端现象。
  - 确认刷新时实际命中的是 `/reader3/getChapterList`，并且请求体确实只有 `refresh`，不是完整的阅读参数。
- 后端先兜底。
  - 先把阅读链路上最关键的几个 POST 取参改成空安全，避免任何“恢复过程中的半截请求”直接把接口打成 NPE。
- 前端再补初始化时机控制。
  - 在 `readingBook.bookUrl` 尚未恢复完成时先挂起阅读初始化，等状态恢复后再继续加载目录和正文。
- 最后再补失败兜底。
  - 即使目录请求失败，也要把错误内容显示出来，不能继续留在白屏态。
- Docker 部署单独处理。
  - 源码版构建优先复用本地 `web/dist`，避免容器里重新 `yarn build` 时受网络影响失败。

### 本轮修改文件
- `src/main/java/com/htmake/reader/api/controller/BookController.kt`
  - 对 `getChapterList`、`saveBookProgress`、`getBookContent` 的 POST 取参加了空安全处理。
- `web/src/views/Reader.vue`
  - 增加 `pendingInit` 守卫，延后阅读页初始化时机。
  - 补了目录加载失败时的前端兜底展示，避免刷新后白屏。
  - 这一轮也继续沿用了滑动分页宽度、列宽、列间距的统一测量逻辑。
- `Dockerfile.source`
  - 调整源码镜像构建流程，优先复用本地已构建的 `web/dist`，缺失时才回退到容器内构建。

### 本轮验证
- 前端 `npm.cmd run build` 已通过。
- `TextFileTest` 仍然保持通过。
- Docker Desktop 源码版容器已重新构建并启动，`http://localhost:18080/` 返回 `200`。
- 定向验证了“只带 `refresh` 的 `/reader3/getChapterList` 请求”。
  - 现在返回的是业务错误 `请输入书籍链接`，不再抛 `NullPointerException`。

### 哪些还没改好
- 左右间距问题目前确认还没有修改完毕。
  - 这次会话里虽然已经把滑动阅读的页宽、正文列宽、列间距统一到同一套测量逻辑上，但从你的实际反馈看，手机阅读页左右安全区/正文宽度的最终表现仍然没有完全对齐。
  - 也就是说，这部分不能记为“已彻底修复”，只能记为“已继续推进，但仍需复测和继续修改”。
- 因此本轮记录里，阅读页相关修改要区分成两部分：
  - “刷新后空白/NPE”这一条已定位并补上兜底。
  - “左右间距异常”这一条目前仍未收口。
## 2026-03-11 会话追加记录（二）

### 修改日期
- 2026-03-11

### 本轮新增问题定位
- 手机上左右滑动阅读页的左右间距异常仍然存在，而且确认不是“两排按钮布局”本身就能解决的问题。
- 上一轮临时把左右浮动按钮改成了底部两排居中布局，但从你的实际反馈看，这个改动反而干扰了问题判断，因此需要先回退。
- 继续排查后确认，手机阅读页里仍有多处 `100vw`、`window.innerWidth`、自动宽度和真实内容容器宽度混用。
- 这些值在 PWA / 移动浏览器环境下不一定完全相等，所以虽然分页逻辑已经不再使用旧的固定值，但正文区、顶部栏、底部栏和滑动分页列宽仍可能出现左右留白不一致。

### 本轮修改思路
- 先回退临时按钮布局。
  - 恢复成上个版本的左右浮动方式，避免把“按钮遮挡正文”和“正文左右间距异常”混成同一个问题。
- 再收紧手机阅读页的宽度来源。
  - 把手机模式下几处仍依赖 `100vw` 的布局改成左右贴边或容器宽度驱动，减少 viewport 计算差异。
- 滑动分页宽度继续改为优先取真实 DOM 宽度。
  - `pageWidth` 优先读取 `.content` 的 `clientWidth`，`contentWidth` 优先读取 `.content-inner` 的 `clientWidth`，只有取不到时才回退到旧逻辑。
- 最后补首屏宽度来源。
  - 将全局 `windowSize.width` 的初始化和 resize 更新改为优先使用 `document.documentElement.clientWidth`，让首次渲染时拿到的宽度更接近真实可视区域。

### 本轮修改文件
- `web/src/views/Reader.vue`
  - 回退了手机模式下两排按钮布局，恢复成左右浮动按钮的旧样式。
  - 将手机模式下 `tool-bar`、`read-bar`、`.chapter`、`.top-bar` 的宽度处理从 `100vw` 调整为左右贴边 / `100%` / `width: auto`。
  - `refreshSlideMetrics()` 改为优先按 `.content` 和 `.content-inner` 的真实 `clientWidth` 计算滑动分页页宽与正文宽。
  - `.chapter.slide-reader .content-inner` 与 `.book-content` 改为显式使用 `--slide-content-width` 和 `100%` fallback，减少自动宽度带来的偏差。
- `web/src/App.vue`
  - `resize` 时更新 `windowSize.width` 改为优先使用 `document.documentElement.clientWidth`。
- `web/src/plugins/vuex.js`
  - 初始 `windowSize.width` 改为优先使用 `document.documentElement.clientWidth`。

### 本轮验证
- 前端 `npm.cmd run build` 已通过。
- 修正了这次改动带出的行尾格式警告后，再次构建通过，剩余仍是项目原本就存在的体积 / Workbox 警告。
- Docker Desktop 源码版容器已重新构建并启动。
- 再次验证 `http://localhost:18080/` 返回 `200`。

### 哪些还没改好
- 这轮已经把“两排按钮布局”回退，并继续把手机阅读页的宽度计算与 CSS 宽度来源统一到了更接近真实容器的一套逻辑上。
- 但左右间距问题是否已经完全收口，仍然需要在实际手机浏览器 / PWA 页面里继续复测确认，不能直接记为“彻底修复”。
- 由于项目启用了 PWA / service worker，前端复测前仍需要注意浏览器缓存影响。
  - 最稳妥的方式仍然是强刷一次，或者直接彻底关闭页面后重开。

## 2026-03-11 会话追加记录（三）

### 修改日期
- 2026-03-11

### 本轮修改思路
- 排查手机端左右间距异常的根因，定位到三个问题点：
  1. `web/src/App.vue` 中 `safeArea` 解析使用 `| 0`（位运算），导致 CSS 值 `"20px"` 被截断为 0。设备安全区偏移在 JS 层始终为 0。
  2. `.content-inner` 同时设置了显式 `width: var(--slide-content-width)` 和 `margin-left/right`，可能导致总宽度与容器宽度不一致。
  3. `.book-content` 的 CSS columns `column-width` 依赖 CSS 变量，可能与实际渲染宽度有偏差。

### 本轮修改文件
- `web/src/App.vue`
  - 将 `safeArea` 的四个方向解析从 `docStyle.getPropertyValue("--sat") | 0` 改为 `parseInt(docStyle.getPropertyValue("--sat"), 10) || 0`。
- `web/src/views/Reader.vue`
  - `.chapter.slide-reader .content-inner`：将 `width: var(--slide-content-width, calc(100% - 32px))` 和 `margin: 0 16px` 改为 `width: auto`，只保留 margin-left/right 的 CSS 变量驱动。
  - `.chapter.slide-reader .book-content`：将 `-webkit-columns` 和 `columns` 的 `column-width` 从 `var(--slide-content-width, calc(100% - 32px))` 改为 `100%`。

### 本轮验证
- `npm.cmd run build` 已通过，无新增编译错误。

### 实际效果（❌ 失败）
- 本次改动 **未修复** 左右间距问题。
- 反而引入了新 bug：
  - **上下滚动**（web 网文式阅读）正常。
  - **上下滑动** 无效。
  - **左右翻页** 无效。
- 原因推测：将 `.content-inner` 改成 `width: auto` 后，CSS multi-column 布局中 `.book-content` 的 `columns: 100% 1` 无法正确计算列宽（`100%` 在 column-width 上下文中含义不同于在 width 属性中），导致分页列宽计算异常，翻页和滑动逻辑产出的 `scrollWidth` / `totalPages` 值不正确。
- 这些改动需要 **回退或重新修正**。

### 当前状态
- 上述改动仍保留在代码中，尚未回退。
- 左右间距问题仍未收口，需要在后续会话中继续排查和修复。

## 2026-03-12 会话追加记录（四）

### 修改日期
- 2026-03-12

### 本轮新增问题定位
- 点击“导入本地书籍”后，移动端弹窗 UI 过大，整体以接近全屏的方式展开。
- 章节列表较长时会持续向下撑开内容区域，导致底部“确定导入”按钮被挤出可视区域，用户无法直接完成导入。
- 当前实现里该弹窗在手机模式下直接使用 `:fullscreen="collapseMenu"`，这与“悬浮窗式确认导入”的交互目标冲突。

### 本轮修改思路
- 先去掉导入弹窗在手机模式下的全屏展开方式，改为固定最大高度的悬浮窗。
- 再将弹窗内部改成纵向 flex 布局，让书籍信息区占上部、章节列表占剩余空间并独立滚动。
- 最后补一层手机端样式压缩：
  - 缩小封面尺寸。
  - 收紧表单项横向占位。
  - 给底部 footer 增加安全区 padding，保证“确定导入”始终可见、可点击。

### 本轮修改文件
- `web/src/views/Index.vue`
  - 将“导入本地书籍”弹窗从手机端全屏模式改为使用 `custom-class="import-book-dialog"` 的悬浮窗模式。
  - 新增 `importBookDialogWidth`、`importBookDialogTop`、`importBookChapterListStyle`，分别控制移动端弹窗宽度、顶部间距和章节列表高度策略。
  - 为导入弹窗增加 `import-book-container` 布局，令章节列表在弹窗内部滚动，不再把 footer 顶出屏幕。
  - 补充移动端样式，压缩封面、表单和目录区域，让“确定导入”按钮保持在可视区域。
- `UPDATE.md`
  - 追加本轮会话记录、验证结果与部署说明。

### 本轮验证
- 前端 `npm.cmd run build` 已通过。
- 构建产物中已包含新的导入弹窗样式和脚本逻辑：
  - `css/index.89db2755.css`
  - `js/index.a3dbf881.js`

### Docker 部署处理
- 尝试使用源码版部署命令重新构建容器：
  - `docker compose -f docker-compose.yml -f docker-compose.source.yml up -d --build reader readerwebview`
- 本轮未能直接完成镜像重建，原因不是代码编译错误，而是外部依赖网络不稳定：
  - 一次失败于 Gradle / Maven 依赖下载的 SSL handshake。
  - 一次失败于 Docker Hub 拉取 `node:16-alpine` 元数据时 EOF。
- 因为这次修改只涉及前端资源，没有后端逻辑变化，所以改为热更新当前运行容器：
  - 从 `reader` 容器导出当前 `/app/bin/reader.jar`。
  - 将本地新构建的 `web/dist` 覆盖进 jar 内 `BOOT-INF/classes/web`。
  - 把补丁后的 jar 回写到容器，并重启 `reader`。

### 部署后确认
- `docker compose -f docker-compose.yml -f docker-compose.source.yml ps` 显示 `reader` 容器已恢复运行。
- `http://localhost:18080/` 返回 `HTTP 200`。
- 线上返回的 `index` 分包资源已经包含本轮改动标记：
  - `import-book-dialog`
  - `importBookDialogWidth`

### 使用说明 / 风险提示
- 由于项目启用了 PWA / service worker，如果终端设备仍看到旧的导入弹窗样式，优先执行一次强刷，或彻底关闭页面后重新打开。
- 本轮线上生效方式是“热补丁当前容器内 jar”，仓库代码已经更新，但后续若重新拉起镜像，仍建议在网络稳定时补做一次标准 Docker 重建，避免运行态与镜像产物脱节。
## 2026-03-13 会话追加记录（五）

### 修改日期
- 2026-03-13

### 本轮新增问题定位
- 安全区数据此前只在 `App.vue` 的 `mounted` 阶段读取一次，而 `Reader.vue` 可能已经先按 `safeArea = 0` 完成了首轮分页计算。
- 这样会出现一个时序错位：CSS 已经按真实安全区收紧了左右留白，但分页宽度、总页数和当前页位移仍沿用旧值，导致手机左右滑动阅读页出现左右间距不一致、页内容错位等现象。
- 同时，窗口尺寸变化时此前只更新了 `windowSize`，没有同步刷新 `safeArea`，因此旋转屏幕或 PWA 视口变化后，左右间距也可能继续使用过期数据。

### 本轮修改思路
- 先把视口高度、窗口尺寸、触摸能力和安全区同步逻辑收敛到同一个入口，保证初始化、`resize`、`mounted` 后补偿这三条链路拿到的是同一套数据。
- 再让阅读页显式监听 `safeArea` 变化，在安全区更新后重新计算分页，并把当前位置重新对齐到当前页，避免 CSS 宽度变了但分页状态没跟上。
- 顺手把阅读页内部几处仍通过位运算读取安全区顶部偏移的逻辑改为统一的数值读取方法，避免后续再混入隐式类型转换。

### 本轮修改文件
- `web/src/App.vue`
  - 新增 `syncSafeArea()` 与 `syncViewportMetrics()`，统一处理 `--vh`、`windowSize`、`touchable`、`safeArea` 同步。
  - 将初始化和 `window.onresize` 的视口同步改为复用 `syncViewportMetrics()`。
  - 在 `mounted` 后再次补跑一次视口同步，覆盖首屏渲染与根组件挂载时序差。
- `web/src/views/Reader.vue`
  - 新增 `safeArea` 计算属性与 watcher，安全区变化后重新执行 `computePages()` 和 `showPage()`。
  - 抽出 `getSafeAreaInset()` 与 `refreshPageLayout()`，统一左右/顶部安全区读取和重排入口。
  - 将滑动阅读页的左右 padding 计算，以及若干顶部定位/滚动偏移逻辑，统一改为走 `getSafeAreaInset()`。

### 本轮验证
- 前端 `npm.cmd run build` 已通过。
- 构建结果无新增编译错误；仍保留项目原本已有的包体积 / Workbox 警告。

### 还需要继续确认的点
- 这轮修复的是“安全区真实值到达后，阅读页没有重新分页”的链路问题，理论上能覆盖一类很像你截图表现的左右间距错位。
- 但是否已经彻底收口，仍需要在实际手机浏览器 / PWA 页面里复测确认，尤其要看：
  - 首次进入阅读页时左右留白是否已经对齐。
  - 刘海屏 / 非对称安全区设备上左右翻页后是否仍会出现上一页内容挤入。
  - 旋转屏幕后分页宽度和正文边距是否会跟着重新对齐。
### 本轮热更新部署
- 已从运行中的 `reader` 容器导出当前 `/app/bin/reader.jar` 到本地 `build/reader-live.jar`。
- 已将本轮新构建的 `web/dist` 更新进 jar 内 `BOOT-INF/classes/web`。
- 已将补丁后的 `reader-live.jar` 回写到容器 `/app/bin/reader.jar`，并执行 `docker restart reader`。

### 部署后确认
- `docker compose -f docker-compose.yml -f docker-compose.source.yml ps` 显示 `reader` 容器已恢复运行。
- `http://localhost:18080/` 返回 `HTTP 200`。
- 首页返回内容已包含这轮新资源标记：
  - `app.0288f93e.js`
  - `reader.6ee311a7.js`
  - `css/app.f0fe790a.css`
## 2026-03-13 会话追加记录（六）

### 修改日期
- 2026-03-13

### 本轮新增问题定位
- 上一轮修掉的是“安全区到达后未重新分页”的时序问题，但你这次反馈说明真正的阅读体验问题还在：正文可用宽度本身仍然过大。
- 也就是说，这一轮的根因不再是“算错了”，而是“就算算对了，正文依然太满、左右留白依然不够”。
- 之前移动端正文横向留白的基础值仍接近 `16px`，在当前字体与主题背景下，版面视觉上还是偏挤，单行承载文字偏多。

### 本轮修改思路
- 不再继续围绕分页偏移做解释型修复，而是直接收窄移动端正文的真实可读宽度。
- 将移动端正文的基础横向留白从固定小边距调整为随视口宽度动态放大的更宽边距。
- 让普通阅读模式和左右滑动分页模式统一吃到这套更宽的边距来源，避免两条链路视觉效果不一致。

### 本轮修改文件
- `web/src/views/Reader.vue`
  - 新增 `mobileReaderPaddingBase()`，按移动端视口宽度动态计算正文基础横向留白，范围约束在 `24px ~ 36px`。
  - `slidePaddingLeft()` / `slidePaddingRight()` 改为基于 `mobileReaderPaddingBase()` 叠加安全区，不再使用原先偏小的固定基础值。
  - 移动端 `.chapter` 的左右 padding 改为跟随 `--slide-padding-left/right`，让普通阅读和滑动阅读使用一致的更宽正文留白。

### 本轮验证
- 前端 `npm.cmd run build` 已通过。
- 热更新后，`http://localhost:18080/` 返回 `HTTP 200`。
- 当前线上首页已包含本轮新资源：
  - `app.fa7092c4.js`
  - `reader.30c453ad.js`
  - `css/app.79bd1202.css`
  - `css/reader.a4176fa4.css`

### 本轮热更新部署
- 已重新导出运行中容器内的 `reader.jar`。
- 已将本轮最新 `web/dist` 覆盖进 jar 内 `BOOT-INF/classes/web`。
- 已将补丁后的 jar 回写至 `reader` 容器并重启容器。

### 还需要继续确认的点
- 这轮已经不是“修复看不见的计算误差”，而是“直接把正文做窄一点”。
- 接下来最关键的确认项只有一个：实际手机 / PWA 页面里，正文左右留白是否已经明显比上一版更宽，版面是否终于不再显得发满、压迫。

## 2026-03-13 Session Addendum (7)

### Date
- 2026-03-13

### Goal
- Keep the mobile reader text column adaptive to viewport and font size.
- Add a user-facing setting so left and right spacing can be adjusted without changing code.

### Approach
- Keep the mobile content width driven by a readable column target instead of letting text fill the whole background area.
- Add a persisted config field named `mobileSidePaddingOffset` and apply it on top of the adaptive mobile padding.
- Expose the new setting in the mobile read settings panel.
- Verify hot deployment by checking both the patched jar contents and the jar copied back from the running container.

### Files Changed
- `web/src/App.vue`
  - Unified viewport and safe-area sync so pagination can react to safe-area updates.
- `web/src/views/Reader.vue`
  - Added safe-area driven re-layout hooks.
  - Added `mobileReaderSidePaddingOffset()` and `mobileReaderAdaptiveContentWidth()`.
  - Changed mobile reader width calculation to use adaptive width plus adjustable side padding.
  - Unified normal and slide reading modes around `--reader-content-max-width`, `--slide-padding-left/right`, and `--slide-content-width`.
- `web/src/components/ReadSettings.vue`
  - Added the mobile-only side spacing control.
  - Added min/max/step rules for `mobileSidePaddingOffset`.
- `web/src/plugins/config.js`
  - Added default `mobileSidePaddingOffset` values for day and night configs.

### Verification
- `npm.cmd run build` passed.
- The running `reader` container was hot updated successfully.
- `http://localhost:18080/` returned `HTTP 200`.
- Live assets now point to:
  - `app.fc3c4053.js`
  - `reader.ed922b4c.js`
  - `css/app.79bd1202.css`
  - `css/reader.5cb83d51.css`
- Live `css/reader.5cb83d51.css` contains:
  - `max-width: var(--reader-content-max-width, 100%)`
  - `width: var(--slide-content-width, var(--reader-content-max-width, calc(100vw - 48px)))`
  - `padding: 0 24px`

### Deployment Notes
- This hot update was verified by checking that the patched jar already referenced the new asset names before copying it into the container.
- After copying, the jar was exported again from `/app/bin/reader.jar` to confirm the running container was using the updated frontend instead of reporting a false-positive deployment.
