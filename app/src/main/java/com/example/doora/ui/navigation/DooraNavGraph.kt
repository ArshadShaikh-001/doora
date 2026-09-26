package com.example.doora.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.addtask.AddEditTaskScreen
import com.example.doora.ui.calendar.CalendarScreen
import com.example.doora.ui.home.HomeScreen
import com.example.doora.ui.statistics.StatisticsScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Calendar : Screen("calendar", "Calendar", Icons.Default.DateRange)
    object Statistics : Screen("statistics", "Stats", Icons.AutoMirrored.Filled.List)
}

@Composable
fun DooraNavGraph(
    navController: NavHostController,
    repository: TaskRepository
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavScreens = listOf(Screen.Home, Screen.Calendar, Screen.Statistics)
    val showBottomBar = currentRoute in bottomNavScreens.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    repository = repository,
                    onAddTask = { navController.navigate("add_task") },
                    onEditTask = { occurrenceId -> navController.navigate("edit_task/$occurrenceId") }
                )
            }

            composable(Screen.Calendar.route) {
                CalendarScreen(
                    repository = repository,
                    onEditTask = { occurrenceId -> navController.navigate("edit_task/$occurrenceId") }
                )
            }

            composable(Screen.Statistics.route) {
                StatisticsScreen(repository = repository)
            }

            composable("add_task") {
                AddEditTaskScreen(
                    repository = repository,
                    occurrenceId = null,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "edit_task/{occurrenceId}",
                arguments = listOf(navArgument("occurrenceId") { type = NavType.LongType })
            ) { backStackEntry ->
                val occurrenceId = backStackEntry.arguments?.getLong("occurrenceId")
                AddEditTaskScreen(
                    repository = repository,
                    occurrenceId = occurrenceId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
