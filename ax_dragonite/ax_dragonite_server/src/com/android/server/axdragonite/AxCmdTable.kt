/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.axdragonite

import java.io.File
import java.util.Collections
import java.util.HashMap

object AxCmdTable {
    const val TYPE_NODE = 0
    const val TYPE_FUNCTION = 1
    const val PARAM_COUNT_NONE = 0
    const val PARAM_COUNT_SINGLE = 1
    const val PARAM_COUNT_PAIR = 2
    const val CPUSET_DIR = "/dev/cpuset"
    const val CPUCTL_DIR = "/dev/cpuctl"
    const val FILE_CGROUP_PROCS = "cgroup.procs"
    const val FILE_TASKS = "tasks"
    const val FILE_CPUS = "cpus"
    const val FILE_CPU_SHARES = "cpu.shares"
    const val FILE_UCLAMP_MIN = "cpu.uclamp.min"
    const val FILE_UCLAMP_MAX = "cpu.uclamp.max"
    const val PATH_SEP = "/"
    const val GROUP_TOP_APP = "top-app"
    const val GROUP_FOREGROUND = "foreground"
    const val GROUP_BACKGROUND = "background"
    const val GROUP_SYSTEM_BACKGROUND = "system-background"
    const val GROUP_RESTRICTED = "restricted"
    const val GROUP_DEX2OAT = "dex2oat"
    const val GROUP_SYSTEM = "system"
    const val GROUP_RT = "rt"
    const val GROUP_AX_FOREGROUND = "ax_foreground"
    const val GROUP_FOREGROUND_WINDOW = "foreground_window"
    const val FUNC_ANIMATIONBOOST = "animationBoost"
    const val FUNC_BACKGROUNDLOADLIMIT = "backgroundLoadLimit"
    const val FUNC_BACKGROUNDPROCESSFREEZE = "backgroundProcessFreeze"
    const val FUNC_KSWAPDBINDCORECONTROLL = "kswapdBindCoreControll"
    const val FUNC_PLATFORMRESOURCECONTROL = "platformResourceControl"
    const val FUNC_PROCESSAFFINITY = "processAffinity"
    const val FUNC_SETAXNAMETHREADAFFINITY = "setAxNameThreadAffinity"
    const val FUNC_SFBINDCORECONTROLL = "sfBindCoreControll"
    const val FUNC_THREADAFFINITY = "threadAffinity"
    const val FUNC_THREADBOOST = "threadBoost"
    const val NAME_CMD_ANIMATION_BOOST = "CMD_ANIMATION_BOOST"
    const val NAME_CMD_AX_NAMED_THREAD_AFFINITY = "CMD_AX_NAMED_THREAD_AFFINITY"
    const val NAME_CMD_BACKGROUND_FREEZE = "CMD_BACKGROUND_FREEZE"
    const val NAME_CMD_BACKGROUND_LOAD_LIMIT = "CMD_BACKGROUND_LOAD_LIMIT"
    const val NAME_CMD_CPUCTL_BG_CPU_SHARES = "CMD_CPUCTL_BG_CPU_SHARES"
    const val NAME_CMD_CPUCTL_BG_PROCS = "CMD_CPUCTL_BG_PROCS"
    const val NAME_CMD_CPUCTL_BG_UCLAMP_MAX = "CMD_CPUCTL_BG_UCLAMP_MAX"
    const val NAME_CMD_CPUCTL_BG_UCLAMP_MIN = "CMD_CPUCTL_BG_UCLAMP_MIN"
    const val NAME_CMD_CPUCTL_DEX_CPU_SHARES = "CMD_CPUCTL_DEX_CPU_SHARES"
    const val NAME_CMD_CPUCTL_DEX_PROCS = "CMD_CPUCTL_DEX_PROCS"
    const val NAME_CMD_CPUCTL_DEX_UCLAMP_MAX = "CMD_CPUCTL_DEX_UCLAMP_MAX"
    const val NAME_CMD_CPUCTL_DEX_UCLAMP_MIN = "CMD_CPUCTL_DEX_UCLAMP_MIN"
    const val NAME_CMD_CPUCTL_FG_CPU_SHARES = "CMD_CPUCTL_FG_CPU_SHARES"
    const val NAME_CMD_CPUCTL_FG_PROCS = "CMD_CPUCTL_FG_PROCS"
    const val NAME_CMD_CPUCTL_FG_UCLAMP_MAX = "CMD_CPUCTL_FG_UCLAMP_MAX"
    const val NAME_CMD_CPUCTL_FG_UCLAMP_MIN = "CMD_CPUCTL_FG_UCLAMP_MIN"
    const val NAME_CMD_CPUCTL_FW_CPU_SHARES = "CMD_CPUCTL_FW_CPU_SHARES"
    const val NAME_CMD_CPUCTL_AXFG_CPU_SHARES = "CMD_CPUCTL_AXFG_CPU_SHARES"
    const val NAME_CMD_CPUCTL_RESTRICTED_CPU_SHARES = "CMD_CPUCTL_RESTRICTED_CPU_SHARES"
    const val NAME_CMD_CPUCTL_RESTRICTED_PROCS = "CMD_CPUCTL_RESTRICTED_PROCS"
    const val NAME_CMD_CPUCTL_RESTRICTED_UCLAMP_MAX = "CMD_CPUCTL_RESTRICTED_UCLAMP_MAX"
    const val NAME_CMD_CPUCTL_RESTRICTED_UCLAMP_MIN = "CMD_CPUCTL_RESTRICTED_UCLAMP_MIN"
    const val NAME_CMD_CPUCTL_ROOT_PROCS = "CMD_CPUCTL_ROOT_PROCS"
    const val NAME_CMD_CPUCTL_RT_CPU_SHARES = "CMD_CPUCTL_RT_CPU_SHARES"
    const val NAME_CMD_CPUCTL_RT_PROCS = "CMD_CPUCTL_RT_PROCS"
    const val NAME_CMD_CPUCTL_RT_UCLAMP_MAX = "CMD_CPUCTL_RT_UCLAMP_MAX"
    const val NAME_CMD_CPUCTL_RT_UCLAMP_MIN = "CMD_CPUCTL_RT_UCLAMP_MIN"
    const val NAME_CMD_CPUCTL_SBG_CPU_SHARES = "CMD_CPUCTL_SBG_CPU_SHARES"
    const val NAME_CMD_CPUCTL_SBG_PROCS = "CMD_CPUCTL_SBG_PROCS"
    const val NAME_CMD_CPUCTL_SBG_UCLAMP_MAX = "CMD_CPUCTL_SBG_UCLAMP_MAX"
    const val NAME_CMD_CPUCTL_SBG_UCLAMP_MIN = "CMD_CPUCTL_SBG_UCLAMP_MIN"
    const val NAME_CMD_CPUCTL_SYS_CPU_SHARES = "CMD_CPUCTL_SYS_CPU_SHARES"
    const val NAME_CMD_CPUCTL_SYS_PROCS = "CMD_CPUCTL_SYS_PROCS"
    const val NAME_CMD_CPUCTL_SYS_UCLAMP_MAX = "CMD_CPUCTL_SYS_UCLAMP_MAX"
    const val NAME_CMD_CPUCTL_SYS_UCLAMP_MIN = "CMD_CPUCTL_SYS_UCLAMP_MIN"
    const val NAME_CMD_CPUCTL_TA_CPU_SHARES = "CMD_CPUCTL_TA_CPU_SHARES"
    const val NAME_CMD_CPUCTL_TA_PROCS = "CMD_CPUCTL_TA_PROCS"
    const val NAME_CMD_CPUCTL_TA_UCLAMP_MAX = "CMD_CPUCTL_TA_UCLAMP_MAX"
    const val NAME_CMD_CPUCTL_TA_UCLAMP_MIN = "CMD_CPUCTL_TA_UCLAMP_MIN"
    const val NAME_CMD_CPUSET_BG_CPUS = "CMD_CPUSET_BG_CPUS"
    const val NAME_CMD_CPUSET_BG_PROCS = "CMD_CPUSET_BG_PROCS"
    const val NAME_CMD_CPUSET_BG_TASKS = "CMD_CPUSET_BG_TASKS"
    const val NAME_CMD_CPUSET_DEX2OAT_CPUS = "CMD_CPUSET_DEX2OAT_CPUS"
    const val NAME_CMD_CPUSET_DEX2OAT_PROCS = "CMD_CPUSET_DEX2OAT_PROCS"
    const val NAME_CMD_CPUSET_DEX2OAT_TASKS = "CMD_CPUSET_DEX2OAT_TASKS"
    const val NAME_CMD_CPUSET_FG_CPUS = "CMD_CPUSET_FG_CPUS"
    const val NAME_CMD_CPUSET_FG_PROCS = "CMD_CPUSET_FG_PROCS"
    const val NAME_CMD_CPUSET_FG_TASKS = "CMD_CPUSET_FG_TASKS"
    const val NAME_CMD_CPUSET_RESTRICTED_CPUS = "CMD_CPUSET_RESTRICTED_CPUS"
    const val NAME_CMD_CPUSET_RESTRICTED_PROCS = "CMD_CPUSET_RESTRICTED_PROCS"
    const val NAME_CMD_CPUSET_RESTRICTED_TASKS = "CMD_CPUSET_RESTRICTED_TASKS"
    const val NAME_CMD_CPUSET_SBG_CPUS = "CMD_CPUSET_SBG_CPUS"
    const val NAME_CMD_CPUSET_SBG_PROCS = "CMD_CPUSET_SBG_PROCS"
    const val NAME_CMD_CPUSET_SBG_TASKS = "CMD_CPUSET_SBG_TASKS"
    const val NAME_CMD_CPUSET_TA_CPUS = "CMD_CPUSET_TA_CPUS"
    const val NAME_CMD_CPUSET_TA_PROCS = "CMD_CPUSET_TA_PROCS"
    const val NAME_CMD_CPUSET_TA_TASKS = "CMD_CPUSET_TA_TASKS"
    const val NAME_CMD_KSWAPD_CONTROLL_AFFINITY = "CMD_KSWAPD_CONTROLL_AFFINITY"
    const val NAME_CMD_PLATFORM_CONTROL = "CMD_PLATFORM_CONTROL"
    const val NAME_CMD_PROCESS_AFFINITY = "CMD_PROCESS_AFFINITY"
    const val NAME_CMD_SF_CONTROL_AFFINITY = "CMD_SF_CONTROL_AFFINITY"
    const val NAME_CMD_THREAD_AFFINITY = "CMD_THREAD_AFFINITY"
    const val NAME_CMD_THREAD_BOOST = "CMD_THREAD_BOOST"
    const val CMD_CPUSET_TA_PROCS = 100
    const val CMD_CPUSET_FG_PROCS = 101
    const val CMD_CPUSET_BG_PROCS = 102
    const val CMD_CPUSET_SBG_PROCS = 103
    const val CMD_CPUSET_RESTRICTED_PROCS = 105
    const val CMD_CPUSET_DEX2OAT_PROCS = 106
    const val CMD_CPUSET_TA_TASKS = 150
    const val CMD_CPUSET_FG_TASKS = 151
    const val CMD_CPUSET_BG_TASKS = 152
    const val CMD_CPUSET_SBG_TASKS = 153
    const val CMD_CPUSET_RESTRICTED_TASKS = 155
    const val CMD_CPUSET_DEX2OAT_TASKS = 156
    const val CMD_CPUSET_TA_CPUS = 200
    const val CMD_CPUSET_FG_CPUS = 201
    const val CMD_CPUSET_BG_CPUS = 202
    const val CMD_CPUSET_SBG_CPUS = 203
    const val CMD_CPUSET_RESTRICTED_CPUS = 205
    const val CMD_CPUSET_DEX2OAT_CPUS = 206
    const val CMD_CPUCTL_SYS_PROCS = 300
    const val CMD_CPUCTL_SBG_PROCS = 301
    const val CMD_CPUCTL_TA_PROCS = 302
    const val CMD_CPUCTL_DEX_PROCS = 303
    const val CMD_CPUCTL_FG_PROCS = 304
    const val CMD_CPUCTL_RT_PROCS = 305
    const val CMD_CPUCTL_BG_PROCS = 306
    const val CMD_CPUCTL_RESTRICTED_PROCS = 307
    const val CMD_CPUCTL_ROOT_PROCS = 308
    const val CMD_CPUCTL_SYS_UCLAMP_MIN = 400
    const val CMD_CPUCTL_SYS_UCLAMP_MAX = 401
    const val CMD_CPUCTL_SBG_UCLAMP_MIN = 402
    const val CMD_CPUCTL_SBG_UCLAMP_MAX = 403
    const val CMD_CPUCTL_TA_UCLAMP_MIN = 404
    const val CMD_CPUCTL_TA_UCLAMP_MAX = 405
    const val CMD_CPUCTL_DEX_UCLAMP_MIN = 406
    const val CMD_CPUCTL_DEX_UCLAMP_MAX = 407
    const val CMD_CPUCTL_FG_UCLAMP_MIN = 408
    const val CMD_CPUCTL_FG_UCLAMP_MAX = 409
    const val CMD_CPUCTL_RT_UCLAMP_MIN = 410
    const val CMD_CPUCTL_RT_UCLAMP_MAX = 411
    const val CMD_CPUCTL_BG_UCLAMP_MIN = 412
    const val CMD_CPUCTL_BG_UCLAMP_MAX = 413
    const val CMD_CPUCTL_RESTRICTED_UCLAMP_MIN = 414
    const val CMD_CPUCTL_RESTRICTED_UCLAMP_MAX = 415
    const val CMD_CPUCTL_SYS_CPU_SHARES = 440
    const val CMD_CPUCTL_SBG_CPU_SHARES = 441
    const val CMD_CPUCTL_TA_CPU_SHARES = 442
    const val CMD_CPUCTL_DEX_CPU_SHARES = 443
    const val CMD_CPUCTL_FG_CPU_SHARES = 444
    const val CMD_CPUCTL_RT_CPU_SHARES = 445
    const val CMD_CPUCTL_BG_CPU_SHARES = 446
    const val CMD_CPUCTL_RESTRICTED_CPU_SHARES = 447
    const val CMD_CPUCTL_AXFG_CPU_SHARES = 450
    const val CMD_CPUCTL_FW_CPU_SHARES = 451
    const val CMD_ANIMATION_BOOST = 500
    const val CMD_THREAD_BOOST = 501
    const val CMD_THREAD_AFFINITY = 600
    const val CMD_PROCESS_AFFINITY = 601
    const val CMD_AX_NAMED_THREAD_AFFINITY = 602
    const val CMD_KSWAPD_CONTROLL_AFFINITY = 603
    const val CMD_BACKGROUND_FREEZE = 700
    const val CMD_BACKGROUND_LOAD_LIMIT = 800
    const val CMD_PLATFORM_CONTROL = 900
    const val CMD_SF_CONTROL_AFFINITY = 1000

    sealed class Command(val commandType: Int, val name: String, val id: Int)

    class NodeCommand(commandName: String, commandId: Int, val nodePath: String) :
        Command(TYPE_NODE, commandName, commandId) {
        val pathFile: File = File(nodePath)
    }

    class FunctionCommand(
        commandName: String,
        commandId: Int,
        val functionName: String,
        val parameterCount: Int,
    ) : Command(TYPE_FUNCTION, commandName, commandId)

    private data class NodeDef(val name: String, val id: Int, val path: String)

    private data class FuncDef(val name: String, val id: Int, val func: String, val params: Int)

    private val commandMap = HashMap<String, Command>()

    init {
        registerAll(nodes())
    }

    private fun nodePath(base: String, group: String?, file: String): String {
        if (group == null) return "$base$PATH_SEP$file"
        return "$base$PATH_SEP$group$PATH_SEP$file"
    }

    private fun nodes(): List<NodeDef> = buildList {
        val cpusetGroups =
            listOf(
                Triple(NAME_CMD_CPUSET_TA_PROCS, CMD_CPUSET_TA_PROCS, GROUP_TOP_APP),
                Triple(NAME_CMD_CPUSET_FG_PROCS, CMD_CPUSET_FG_PROCS, GROUP_FOREGROUND),
                Triple(NAME_CMD_CPUSET_BG_PROCS, CMD_CPUSET_BG_PROCS, GROUP_BACKGROUND),
                Triple(NAME_CMD_CPUSET_SBG_PROCS, CMD_CPUSET_SBG_PROCS, GROUP_SYSTEM_BACKGROUND),
                Triple(
                    NAME_CMD_CPUSET_RESTRICTED_PROCS,
                    CMD_CPUSET_RESTRICTED_PROCS,
                    GROUP_RESTRICTED,
                ),
                Triple(NAME_CMD_CPUSET_DEX2OAT_PROCS, CMD_CPUSET_DEX2OAT_PROCS, GROUP_DEX2OAT),
            )
        val taskIds =
            mapOf(
                GROUP_TOP_APP to CMD_CPUSET_TA_TASKS,
                GROUP_FOREGROUND to CMD_CPUSET_FG_TASKS,
                GROUP_BACKGROUND to CMD_CPUSET_BG_TASKS,
                GROUP_SYSTEM_BACKGROUND to CMD_CPUSET_SBG_TASKS,
                GROUP_RESTRICTED to CMD_CPUSET_RESTRICTED_TASKS,
                GROUP_DEX2OAT to CMD_CPUSET_DEX2OAT_TASKS,
            )
        val cpuIds =
            mapOf(
                GROUP_TOP_APP to CMD_CPUSET_TA_CPUS,
                GROUP_FOREGROUND to CMD_CPUSET_FG_CPUS,
                GROUP_BACKGROUND to CMD_CPUSET_BG_CPUS,
                GROUP_SYSTEM_BACKGROUND to CMD_CPUSET_SBG_CPUS,
                GROUP_RESTRICTED to CMD_CPUSET_RESTRICTED_CPUS,
                GROUP_DEX2OAT to CMD_CPUSET_DEX2OAT_CPUS,
            )
        val taskNames =
            mapOf(
                GROUP_TOP_APP to NAME_CMD_CPUSET_TA_TASKS,
                GROUP_FOREGROUND to NAME_CMD_CPUSET_FG_TASKS,
                GROUP_BACKGROUND to NAME_CMD_CPUSET_BG_TASKS,
                GROUP_SYSTEM_BACKGROUND to NAME_CMD_CPUSET_SBG_TASKS,
                GROUP_RESTRICTED to NAME_CMD_CPUSET_RESTRICTED_TASKS,
                GROUP_DEX2OAT to NAME_CMD_CPUSET_DEX2OAT_TASKS,
            )
        val cpuNames =
            mapOf(
                GROUP_TOP_APP to NAME_CMD_CPUSET_TA_CPUS,
                GROUP_FOREGROUND to NAME_CMD_CPUSET_FG_CPUS,
                GROUP_BACKGROUND to NAME_CMD_CPUSET_BG_CPUS,
                GROUP_SYSTEM_BACKGROUND to NAME_CMD_CPUSET_SBG_CPUS,
                GROUP_RESTRICTED to NAME_CMD_CPUSET_RESTRICTED_CPUS,
                GROUP_DEX2OAT to NAME_CMD_CPUSET_DEX2OAT_CPUS,
            )
        for ((name, id, group) in cpusetGroups) {
            add(NodeDef(name, id, nodePath(CPUSET_DIR, group, FILE_CGROUP_PROCS)))
            add(
                NodeDef(
                    taskNames[group]!!,
                    taskIds[group]!!,
                    nodePath(CPUSET_DIR, group, FILE_TASKS),
                )
            )
            add(NodeDef(cpuNames[group]!!, cpuIds[group]!!, nodePath(CPUSET_DIR, group, FILE_CPUS)))
        }
        val cpuctlProcs =
            listOf(
                Triple(NAME_CMD_CPUCTL_SYS_PROCS, CMD_CPUCTL_SYS_PROCS, GROUP_SYSTEM),
                Triple(NAME_CMD_CPUCTL_SBG_PROCS, CMD_CPUCTL_SBG_PROCS, GROUP_SYSTEM_BACKGROUND),
                Triple(NAME_CMD_CPUCTL_TA_PROCS, CMD_CPUCTL_TA_PROCS, GROUP_TOP_APP),
                Triple(NAME_CMD_CPUCTL_DEX_PROCS, CMD_CPUCTL_DEX_PROCS, GROUP_DEX2OAT),
                Triple(NAME_CMD_CPUCTL_FG_PROCS, CMD_CPUCTL_FG_PROCS, GROUP_FOREGROUND),
                Triple(NAME_CMD_CPUCTL_RT_PROCS, CMD_CPUCTL_RT_PROCS, GROUP_RT),
                Triple(NAME_CMD_CPUCTL_BG_PROCS, CMD_CPUCTL_BG_PROCS, GROUP_BACKGROUND),
                Triple(
                    NAME_CMD_CPUCTL_RESTRICTED_PROCS,
                    CMD_CPUCTL_RESTRICTED_PROCS,
                    GROUP_RESTRICTED,
                ),
            )
        for ((name, id, group) in cpuctlProcs) {
            add(NodeDef(name, id, nodePath(CPUCTL_DIR, group, FILE_CGROUP_PROCS)))
        }
        add(
            NodeDef(
                NAME_CMD_CPUCTL_ROOT_PROCS,
                CMD_CPUCTL_ROOT_PROCS,
                nodePath(CPUCTL_DIR, null, FILE_CGROUP_PROCS),
            )
        )
        val cpuctlShares =
            listOf(
                Triple(NAME_CMD_CPUCTL_SYS_CPU_SHARES, CMD_CPUCTL_SYS_CPU_SHARES, GROUP_SYSTEM),
                Triple(
                    NAME_CMD_CPUCTL_SBG_CPU_SHARES,
                    CMD_CPUCTL_SBG_CPU_SHARES,
                    GROUP_SYSTEM_BACKGROUND,
                ),
                Triple(NAME_CMD_CPUCTL_TA_CPU_SHARES, CMD_CPUCTL_TA_CPU_SHARES, GROUP_TOP_APP),
                Triple(NAME_CMD_CPUCTL_DEX_CPU_SHARES, CMD_CPUCTL_DEX_CPU_SHARES, GROUP_DEX2OAT),
                Triple(NAME_CMD_CPUCTL_FG_CPU_SHARES, CMD_CPUCTL_FG_CPU_SHARES, GROUP_FOREGROUND),
                Triple(NAME_CMD_CPUCTL_RT_CPU_SHARES, CMD_CPUCTL_RT_CPU_SHARES, GROUP_RT),
                Triple(NAME_CMD_CPUCTL_BG_CPU_SHARES, CMD_CPUCTL_BG_CPU_SHARES, GROUP_BACKGROUND),
                Triple(
                    NAME_CMD_CPUCTL_RESTRICTED_CPU_SHARES,
                    CMD_CPUCTL_RESTRICTED_CPU_SHARES,
                    GROUP_RESTRICTED,
                ),
                Triple(
                    NAME_CMD_CPUCTL_AXFG_CPU_SHARES,
                    CMD_CPUCTL_AXFG_CPU_SHARES,
                    GROUP_AX_FOREGROUND,
                ),
                Triple(
                    NAME_CMD_CPUCTL_FW_CPU_SHARES,
                    CMD_CPUCTL_FW_CPU_SHARES,
                    GROUP_FOREGROUND_WINDOW,
                ),
            )
        for ((name, id, group) in cpuctlShares) {
            add(NodeDef(name, id, nodePath(CPUCTL_DIR, group, FILE_CPU_SHARES)))
        }
        val uclampGroups =
            listOf(
                Triple(NAME_CMD_CPUCTL_SYS_UCLAMP_MIN, CMD_CPUCTL_SYS_UCLAMP_MIN, GROUP_SYSTEM),
                Triple(
                    NAME_CMD_CPUCTL_SBG_UCLAMP_MIN,
                    CMD_CPUCTL_SBG_UCLAMP_MIN,
                    GROUP_SYSTEM_BACKGROUND,
                ),
                Triple(NAME_CMD_CPUCTL_TA_UCLAMP_MIN, CMD_CPUCTL_TA_UCLAMP_MIN, GROUP_TOP_APP),
                Triple(NAME_CMD_CPUCTL_DEX_UCLAMP_MIN, CMD_CPUCTL_DEX_UCLAMP_MIN, GROUP_DEX2OAT),
                Triple(NAME_CMD_CPUCTL_FG_UCLAMP_MIN, CMD_CPUCTL_FG_UCLAMP_MIN, GROUP_FOREGROUND),
                Triple(NAME_CMD_CPUCTL_RT_UCLAMP_MIN, CMD_CPUCTL_RT_UCLAMP_MIN, GROUP_RT),
                Triple(NAME_CMD_CPUCTL_BG_UCLAMP_MIN, CMD_CPUCTL_BG_UCLAMP_MIN, GROUP_BACKGROUND),
                Triple(
                    NAME_CMD_CPUCTL_RESTRICTED_UCLAMP_MIN,
                    CMD_CPUCTL_RESTRICTED_UCLAMP_MIN,
                    GROUP_RESTRICTED,
                ),
            )
        val uclampMaxNames =
            mapOf(
                CMD_CPUCTL_SYS_UCLAMP_MIN to NAME_CMD_CPUCTL_SYS_UCLAMP_MAX,
                CMD_CPUCTL_SBG_UCLAMP_MIN to NAME_CMD_CPUCTL_SBG_UCLAMP_MAX,
                CMD_CPUCTL_TA_UCLAMP_MIN to NAME_CMD_CPUCTL_TA_UCLAMP_MAX,
                CMD_CPUCTL_DEX_UCLAMP_MIN to NAME_CMD_CPUCTL_DEX_UCLAMP_MAX,
                CMD_CPUCTL_FG_UCLAMP_MIN to NAME_CMD_CPUCTL_FG_UCLAMP_MAX,
                CMD_CPUCTL_RT_UCLAMP_MIN to NAME_CMD_CPUCTL_RT_UCLAMP_MAX,
                CMD_CPUCTL_BG_UCLAMP_MIN to NAME_CMD_CPUCTL_BG_UCLAMP_MAX,
                CMD_CPUCTL_RESTRICTED_UCLAMP_MIN to NAME_CMD_CPUCTL_RESTRICTED_UCLAMP_MAX,
            )
        for ((name, id, group) in uclampGroups) {
            add(NodeDef(name, id, nodePath(CPUCTL_DIR, group, FILE_UCLAMP_MIN)))
            add(NodeDef(uclampMaxNames[id]!!, id + 1, nodePath(CPUCTL_DIR, group, FILE_UCLAMP_MAX)))
        }
    }

    private fun funcs(): List<FuncDef> =
        listOf(
            FuncDef(
                NAME_CMD_THREAD_AFFINITY,
                CMD_THREAD_AFFINITY,
                FUNC_THREADAFFINITY,
                PARAM_COUNT_PAIR,
            ),
            FuncDef(
                NAME_CMD_PROCESS_AFFINITY,
                CMD_PROCESS_AFFINITY,
                FUNC_PROCESSAFFINITY,
                PARAM_COUNT_PAIR,
            ),
            FuncDef(
                NAME_CMD_AX_NAMED_THREAD_AFFINITY,
                CMD_AX_NAMED_THREAD_AFFINITY,
                FUNC_SETAXNAMETHREADAFFINITY,
                PARAM_COUNT_NONE,
            ),
            FuncDef(
                NAME_CMD_KSWAPD_CONTROLL_AFFINITY,
                CMD_KSWAPD_CONTROLL_AFFINITY,
                FUNC_KSWAPDBINDCORECONTROLL,
                PARAM_COUNT_PAIR,
            ),
            FuncDef(
                NAME_CMD_SF_CONTROL_AFFINITY,
                CMD_SF_CONTROL_AFFINITY,
                FUNC_SFBINDCORECONTROLL,
                PARAM_COUNT_NONE,
            ),
            FuncDef(
                NAME_CMD_ANIMATION_BOOST,
                CMD_ANIMATION_BOOST,
                FUNC_ANIMATIONBOOST,
                PARAM_COUNT_PAIR,
            ),
            FuncDef(NAME_CMD_THREAD_BOOST, CMD_THREAD_BOOST, FUNC_THREADBOOST, PARAM_COUNT_PAIR),
            FuncDef(
                NAME_CMD_BACKGROUND_FREEZE,
                CMD_BACKGROUND_FREEZE,
                FUNC_BACKGROUNDPROCESSFREEZE,
                PARAM_COUNT_NONE,
            ),
            FuncDef(
                NAME_CMD_BACKGROUND_LOAD_LIMIT,
                CMD_BACKGROUND_LOAD_LIMIT,
                FUNC_BACKGROUNDLOADLIMIT,
                PARAM_COUNT_SINGLE,
            ),
            FuncDef(
                NAME_CMD_PLATFORM_CONTROL,
                CMD_PLATFORM_CONTROL,
                FUNC_PLATFORMRESOURCECONTROL,
                PARAM_COUNT_SINGLE,
            ),
        )

    private fun registerAll(nodeDefs: List<NodeDef>) {
        for (def in nodeDefs) {
            commandMap[def.name] = NodeCommand(def.name, def.id, def.path)
        }
        for (def in funcs()) {
            commandMap[def.name] = FunctionCommand(def.name, def.id, def.func, def.params)
        }
    }

    @JvmStatic fun getCommands(): Map<String, Command> = Collections.unmodifiableMap(commandMap)

    @JvmStatic fun getCommand(name: String): Command? = commandMap[name]
}
