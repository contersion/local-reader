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
