# 本次实际验证记录

日期：2026-10-02（Asia/Shanghai）。以下全部来自本次重新执行，没有引用历史测试结果代替本次测试。

## 执行环境与复现

Windows、Java 17.0.1、Maven 3.9.11。标准环境在仓库根目录执行：

```bash
mvn test
mvn compile exec:java "-Dexec.mainClass=Main"
mvn compile exec:java "-Dexec.mainClass=cascade.CascadeMain"
mvn compile exec:java "-Dexec.mainClass=network.NetworkMain"
```

**最终按上述四条标准命令原样运行，全部成功；不需要额外缓存或settings参数。** 实际最后一次测试为39项，0失败、0错误、0跳过。

前期沙箱对JAR真实路径读取有限制，先用隔离的本地依赖缓存和临时HTTPS Central settings验证，命令记录如下（临时文件在仓库外，不提交）：

```text
mvn -o -B -s ../maven-settings.xml -gs ../maven-settings.xml "-Dmaven.repo.local=../maven-repo" test
mvn -o -B -s ../maven-settings.xml -gs ../maven-settings.xml "-Dmaven.repo.local=../maven-repo" compile exec:java "-Dexec.mainClass=Main"
mvn -o -B -s ../maven-settings.xml -gs ../maven-settings.xml "-Dmaven.repo.local=../maven-repo" compile exec:java "-Dexec.mainClass=cascade.CascadeMain"
mvn -o -B -s ../maven-settings.xml -gs ../maven-settings.xml "-Dmaven.repo.local=../maven-repo" exec:java "-Dexec.mainClass=network.NetworkMain"
```

离线缓存只是依赖来源，所有当前源码与测试均在本次重新编译、执行。没有使用旧class或旧测试结果。随后验证标准命令时，发现本机Maven全局profile将编译级别覆盖成Java8；已在pom中显式固定compiler插件3.13.0和Java17，再以标准命令重跑三个入口和测试全部成功。没有改动用户的全局Maven配置。需要中文控制台时在当前PowerShell设置 `$env:MAVEN_OPTS='-Dfile.encoding=UTF-8'`。

## 自动化测试：39项，0失败、0错误、0跳过

| 测试类 | 数量 | 主要证据 |
| --- | ---: | --- |
| CascadeDataReaderTest | 6 | 输入完整性、曲线和参数校验 |
| CascadeDpTest | 8 | 双库独立穷举、上下游含弃水耦合、严格终点、变时长 |
| HydroPhysicsTest | 9 | 手算、流量与装机限制、弃水、水头、水位边界；极小负泄流也不截断 |
| LinearCurveTest | 3 | 插值、范围及数据有效性 |
| NetworkDpTest | 8 | 四库逐期/全流域守恒、边界、终点、汇总、拓扑、无解、CSV及独立穷举 |
| SingleDpTest | 5 | 月内历史状态、独立穷举、原数据约束、尾水补点、实际旬长、无解 |

### 独立全路径穷举

Oracle不调用生产版HydroPhysics，也不调用生产DP的转移函数或读取前驱/价值表。直接枚举完整路径，使用独立写出的水量和发电公式：

| 算例 | 完整路径数 | 可行路径数 | 穷举与DP一致的最优值MWh |
| --- | ---: | ---: | ---: |
| 四库，每库2点、3期，1/1/1天 | 256 | 224 | 4380.224064 |
| 四库，每库2点、3期，1/0.5/2天 | 256 | 224 | 3995.148396 |
| 单库5点、6期，月极差0m | 3125 | 5 | 228.720000 |
| 单库5点、6期，月极差10m | 3125 | 59 | 288.000000 |
| 单库5点、6期，月极差20m | 3125 | 144 | 288.000000 |
| 单库5点、6期，月极差30m | 3125 | 190 | 288.000000 |
| 双库原有小例，每库3点、3期，1/1/1天 | 81 | 64 | 1367.512111111 |

这些结果证明相应有限网格算例的一致性，不构成连续调度最优性证明，也不意味着任意新增物理模型都已验证。

## 三个入口的本次结果

| 项目 | 单库 | 双库 | 四库 |
| --- | ---: | ---: | ---: |
| 期数 | 36 | 36 | 6 |
| 总时长/天 | 365 | 365 | 6 |
| 总电量/MWh | 17803905.702958193 | 1182374.198851569 | 8697.303120000 |
| 最大逐库水量残差/m³ | 2.384e-7 | 2.049e-8 | 9.313e-10 |
| 求解入口退出 | 成功 | 成功 | 成功 |

双库最大汇流关系残差为1.421e-14 m³/s。四库逐期检查B=A总出库+D总出库+B区间，C=B总出库+C区间；A、D均出现正弃水，测试确认弃水也全部传入下游。另验证全流域外部来水减C出库等于四库总蓄水变化。

三个案例均核对逐期水量、发电流量+弃水=总出库、上下限、出力、时长换算、相邻期连续性和严格初末状态。总电量与各期各库求和一致；四库还重新读取输出CSV求和。CSV四舍五入后重新计算残差时会略有变化，不能要求十进制文本运算精确为0。

四库分库电量：A 2234.052480、B 3130.305504、C 2613.346320、D 719.598816 MWh。第2期手算2165.063280 MWh，见[教程](network-tutorial.md#6-用实际输出手算第2期包含上游弃水)。

## 无解与错误输入

测试实际触发并断言两种无解情况：某期无任何可达状态；仍有可达状态但严格目标末水位不可达。均抛出包含原因的异常，不自动放宽水位或截断流量。还实际验证了环、自环、未知节点、重复连接和未建模分流被拒绝。

## 发现并处理的旧问题

单库最初试运行因尾水曲线不能覆盖188 m³/s下限被正确拒绝。检查原Excel确认原读取器漏读首个有效数据行、低流量使用边界夹取。已修复行号并补充明确标注的合成点，随后重跑单库及全部测试成功，原Excel内容未改。

单库历史约束、时长、严格末态、统一物理与旧结果区别见[修复说明](single-tutorial.md#本次修复了什么)。共用物理函数取消微小越界泄流的夹取，双库重新运行后代表性CSV与原结果相同。

另外实际运行了可选的旧ChartGenerator：原Excel参考水位在第20期反算出的总下泄违反188～5000 m³/s约束，现已明确拒绝生成具有误导性的发电量提升图。该输入问题没有通过改参考数据或截断流量掩盖；它不影响三个优化入口及其结果CSV。仓库原figures保留为历史图，不作为本次成果。

## 仓库检查

三个案例输出互相隔离；保留代表性CSV，忽略target、class、IDE设置、临时缓存和新生成的辅助Excel/图片。提交前检查Markdown相对链接与git diff --check，并核对原Excel及历史结果仍保留。合并后再次读取远程默认分支，检查首页、教程、入口、数据和CSV的完整性。
