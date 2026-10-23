# NiuMa 厂商接入 —— 结算能力现状

> 更新：2026-10-23
> 目的：记录各游戏「是否有金币输赢」「是否有对局明细落库」，作为结算 Provider 开发依据。
> 结论来自源码与数据库结构核查，非推测。

## 一、为什么需要这份文档

聚合平台需要「逐局结算」才能做流水统计、返水、打码、风控。
但 NiuMa 各游戏的结算模型**并不统一**：

- 有的按**金币输赢**结算，并把明细写进数据库 → 可直接对接
- 有的按**钻石门票**收费，没有金币输赢 → 无法产出结算流水
- 有的**在内存算钱但不落明细** → 拿不到逐局数据

不了解这些差异，会误以为「加个 Provider 就行」。

## 二、各游戏结算能力总表

| 游戏 | gameType | 金币输赢 | 对局明细落库 | 可对接结算 | 说明 |
|---|---|---|---|---|---|
| 跑得快 Lackey | 1028 | ✅ | ✅ `game_lackey_round` + `game_lackey_round_player` | ✅ **已实现** | 主表 + 子表结构 |
| 麻将 Mahjong | 1021 | ✅ | ✅ `game_mahjong_record` | ✅ **已实现** | 宽表（player_id0~3 / win_gold0~3） |
| 比鸡 BiJi | 1027 | ✅ 内存计算（含 1% 抽水） | ❌ **只更新玩家押金，不记录逐局明细** | ❌ 数据不足 | 需改 C++ 加明细落库 |
| 掼蛋 GuanDan | 1030 | ❌ **只扣钻石门票**（2 钻/局） | 仅记录升级等级 `grade_point_red/blue` | ❌ 无金币 | 见第四节 |
| 斗地主 DouDiZhu | 1022 | ❌ | ❌ | ❌ | C++ 未实现金币结算 |
| 牛牛 / 百人牛牛 | 1023/1024 | ❌ | ❌ | ❌ | 同上 |
| 红黑大战 RedBlack | 1025 | ❌ | ❌ | ❌ | 同上 |
| 炸金花 ZhaJinHua | 1026 | ❌ | ❌ | ❌ | 同上 |
| 拖拉机 Tractor | 1029 | ❌ | ❌ | ❌ | 同上 |

**核对方法**：
```bash
# 查哪些游戏 C++ 侧有金币逻辑
cd niuma/server && for g in Lackey Mahjong NiuNiu100 BiJi RedBlack ZhaJinHua Tractor DouDiZhu; do
  echo "$g: $(grep -rl 'winGold\|WinGold' Server/$g/ 2>/dev/null | wc -l) 文件"
done
```
结果：仅 `Lackey`、`BiJi` 含 `winGold`。

## 三、已实现的结算 Provider

| Provider | 游戏 | 表 | 特点 |
|---|---|---|---|
| `LackeySettleProvider` | 跑得快 | `game_lackey_round` JOIN `_round_player` | 主表+子表，一次 JOIN 取出 |
| `MahjongSettleProvider` | 麻将 | `game_mahjong_record` | **宽表拆平**：1 行 → 最多 4 条玩家记录；空座跳过 |

### 实测结果（2026-10-23）

造 1 条麻将对局（4 玩家，赢家 +1500，三家各 -500）：

```
1021:12:vendor_test_001 | 15.0000 |  1500 | MAHJONG
1021:12:vendor_test_002 | -5.0000 |  -500 | MAHJONG
1021:12:vendor_test_003 | -5.0000 |  -500 | MAHJONG
1021:12:vendor_test_004 | -5.0000 |  -500 | MAHJONG
```

10 秒内推送落库，宽表正确拆分 ✅

## 四、掼蛋的特殊性（需业务决策）

掼蛋与其余游戏模型不同：

- **无金币输赢**：C++ 侧只记 `grade_point_red/blue`（升级到几级）
- **收费方式**：每局扣 2 钻石（`deductDiamond()`），类似"桌费"
- **table**：`game_guan_dan_round` 只有队伍得分，无个人金币

**因此掼蛋接入聚合平台前，必须先定业务口径：**

| 方案 | 说明 | 代价 |
|---|---|---|
| 甲：钻石门票制 | 掼蛋用钻石玩，不走金币转账 | 中心钱包→金币的链路对掼蛋无意义 |
| 乙：金币输赢制 | 按输赢结算金币 | **需改 NiuMa C++ 加金币结算** |
| 丙：不纳入聚合 | 掼蛋留给独立运营（Unity 端） | 聚合端无掼蛋 |

**待用户决策**（用户已于 2026-10-23 表示「掼蛋留到后面做」，暂缓）。

## 五、对聚合平台上线的影响

| 影响面 | 说明 |
|---|---|
| 有结算数据的游戏 | 仅跑得快、麻将 |
| 其余游戏 | 玩家可玩，但**平台无流水记录** → 无法算返水/打码/风控 |
| 建议 | 若要上线其余游戏，需先确认其结算模型，或接受"仅门票收入、无逐局流水" |

## 六、新增游戏的接入清单

1. 确认 C++ 侧是否计算金币（`grep winGold`）
2. 确认是否落库（看 `insert into game_xxx_round`）
3. 表结构：主表+子表 / 宽表 / 单表
4. 实现 `SettleProvider`（`fetchSince(sinceMillis, limit)`）
5. 注册为 Spring `@Component`，`SettlePushJob` 会自动发现
6. 用测试数据验证推送落库
