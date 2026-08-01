# BiomeBounty - 群系悬赏系统

基于 Minecraft 生物群系的每日悬赏任务插件。自动扫描服务器群系数据，根据群系资源动态生成难度递进的任务组。

## 功能特性

- ✅ 自动扫描服务器 jar + mod + datapacks 提取群系数据
- ✅ 根据群系资源（树木/矿石/植物/生物）自动生成任务
- ✅ 支持 mod 群系（暮色森林等）
- ✅ Paper 原生插件（paper-plugin.yml）
- ✅ SQLite + MySQL 双数据库支持
- ✅ PlaceholderAPI 变量支持
- ✅ 多语言支持（中文简体/英文）
- ✅ 完整的权限系统
- ✅ 任务超时和冷却机制
- ✅ 排行榜系统

## 算法原理

### 1. 群系资源提取

启动时扫描服务器 JAR、Mod JAR、Datapacks 中所有 biome JSON，提取群系资源：

```
biome JSON → features[] → placed feature → configured feature → config JSON
  → 递归遍历 JSON 树，提取所有 state.Name 方块引用
  → 按关键词分类为 trees / ores / plants
  → 若某类为空，回退到 feature 名称关键词匹配
```

输出：每个群系 $B_i$ 的资源集合 $\{T_i, O_i, P_i, M_i\}$（树木、矿石、植物、生物）。

### 2. 难度计算

每日生成 $N=6$ 组任务，第 $i$ 组的目标难度 $D_i$ 由五项叠加：

$$D_i = \text{clamp}_{[10,95]}\left( B_w + P_i + F_p + R + \varepsilon \right)$$

#### 2.1 周基准 $B_w$

$w \in \{1,...,7\}$ 为星期几（1=周一），预定义映射：

| $w$ | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|-----|---|---|---|---|---|---|---|
| $B_w$ | 30 | 45 | 60 | 35 | 65 | 80 | 50 |

设计：周三/周六为高峰，周四为喘息日，周日收尾。

#### 2.2 组序递进 $P_i$

$$P_i = i \times (8 + U(0,4))$$

其中 $U(0,4)$ 为 $[0,4]$ 均匀随机整数。组序越大难度越高，每组递增 8~12 点。

#### 2.3 玩家画像修正 $F_p$

设完成率为 $r$，近期放弃次数为 $a$：

$$F_p = \begin{cases} +10 & r \geq 0.9 \\ -15 & r < 0.6 \\ 0 & \text{otherwise} \end{cases} \quad + \quad \begin{cases} -10 & a \geq 3 \\ 0 & \text{otherwise} \end{cases}$$

#### 2.4 轻松阀 $R$

两项独立触发：

$$R = \begin{cases} -25 & \text{with } p=0.1 \\ -30 & \text{if } C \geq 3 \wedge i \geq 3 \\ 0 & \text{otherwise} \end{cases}$$

$C$ 为连续全完成天数。连续高压 3 天以上时，后半段强制降难度防劝退。两项不叠加，取先触发者。

#### 2.5 随机抖动 $\varepsilon$

$$\varepsilon \sim U(-5, 5)$$

### 3. 每组任务数

目标难度 $D_i$ 决定组内任务数 $n_i$：

$$n_i = 3 + \left\lfloor \frac{D_i}{15} \right\rfloor$$

| $D_i$ | 10 | 30 | 45 | 60 | 80 | 95 |
|--------|----|----|----|----|----|----|
| $n_i$  | 3  | 5  | 6  | 7  | 8  | 9  |

### 4. 任务选择

从群系资源池中为目标难度选出 $n_i$ 个任务，约束组内不重复类型。

#### 4.1 类型优先级排序

设当天已用类型集合为 $S_{\text{used}}$，对类型 $t$ 定义优先级：

$$\text{priority}(t) = \begin{cases} 0 & t \notin S_{\text{used}} \\ 1 & t \in S_{\text{used}} \end{cases}$$

按 priority 升序排列（未用过的类型优先），同 priority 内随机打乱。

#### 4.2 加权随机选取

对候选任务 $c$，计算其中点难度：

$$d_c = \frac{\min_c + \max_c}{2 \cdot M_t} \times 100$$

其中 $M_t$ 为该类型默认上限（collect/kill: 15, fish: 8, breed: 5, farm: 10, explore: 1）。

选取权重：

$$w_c = \frac{1}{1 + \frac{|d_c - D_i|}{10}} + \delta$$

修正项 $\delta$：

$$\delta = \begin{cases} +0.3 & d_c < 30 \\ 0 & \text{otherwise} \end{cases} \quad \times \quad \begin{cases} \times 0.3 & d_c > 80 \wedge D_i < 40 \\ \times 1 & \text{otherwise} \end{cases}$$

低难度候选保底加权，高难度候选在低目标时压制。按权重轮盘赌选取。

### 5. 数量校准

任务目标数量在 $[\min, \max]$ 范围内按难度线性插值：

$$A = \min + \text{round}\left((\max - \min) \cdot \frac{D_i}{100}\right)$$

$$A = \text{clamp}_{[\min, \max]}(A)$$

### 6. 标题生成

$$\text{title}(i, B) = \text{prefix}(i) + \text{"·"} + B$$

$$\text{prefix}(i) = \begin{cases} \text{入门} & i = 0 \\ \text{基础} & i = 1 \\ \text{进阶} & i = 2 \\ \text{挑战} & i = 3 \\ \text{精英} & i = 4 \\ \text{试炼} & i = 5 \\ \text{终极} & i \geq 6 \end{cases}$$

## 环境要求

- **Paper 1.21+**（不支持 Spigot/旧版）
- Java 21+
- PlaceholderAPI（可选）

## 安装

1. 下载 BiomeBounty.jar
2. 放入服务器 `plugins/` 目录
3. 重启服务器
4. 编辑 `plugins/BiomeBounty/config.yml` 配置
5. 使用 `/bountyadmin reload` 重载配置

## 命令

### 玩家命令
- `/bounty` - 打开全局任务面板
- `/bounty <群系>` - 打开指定群系悬赏
- `/bounty help` - 显示帮助

### 管理员命令
- `/bountyadmin reload` - 重载配置
- `/bountyadmin reset <玩家>` - 重置玩家任务
- `/bountyadmin resetall` - 重置所有任务
- `/bountyadmin scan` - 扫描群系
- `/bountyadmin debug` - 切换调试模式

## 权限

### 群系权限
- `biomebounty.biome.*` - 访问所有群系
- `biomebounty.biome.minecraft.*` - 访问所有原版群系
- `biomebounty.biome.minecraft.forest` - 访问森林群系

### 任务权限
- `biomebounty.task.*` - 接取所有任务类型
- `biomebounty.task.collect` - 采集类任务
- `biomebounty.task.kill` - 击杀类任务
- `biomebounty.task.explore` - 探索类任务

### GUI 权限
- `biomebounty.gui.board` - 打开悬赏板 GUI
- `biomebounty.gui.global` - 打开全局任务面板

### 管理权限
- `biomebounty.admin.*` - 所有管理权限
- `biomebounty.admin.reload` - 重载配置
- `biomebounty.admin.reset` - 重置任务

## PlaceholderAPI 变量

- `%biomebounty_task_count%` - 当前任务数量
- `%biomebounty_task_completed%` - 已完成任务数
- `%biomebounty_total_completed%` - 总完成任务数
- `%biomebounty_streak%` - 连续完成天数
- `%biomebounty_rank%` - 玩家排名

## 配置

详见 `config.yml` 文件。

## 数据库

### SQLite（默认）
```yaml
database:
  type: sqlite
  sqlite:
    file: data.db
```

### MySQL
```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: biomebounty
    username: root
    password: ""
```

## 开发

### 构建
```bash
./gradlew build
```

### 运行测试
```bash
./gradlew test
```

### 依赖
- Paper API 1.21.4
- PlaceholderAPI（可选）

## 许可证

MIT License
