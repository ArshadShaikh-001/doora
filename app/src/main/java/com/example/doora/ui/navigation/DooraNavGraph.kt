package com.example.doora.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.doora.R
import com.example.doora.data.model.RecurrenceType
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.addtask.AddEditTaskScreen
import com.example.doora.ui.calendar.CalendarScreen
import com.example.doora.ui.home.HomeScreen
import com.example.doora.ui.statistics.StatisticsScreen
import com.example.doora.ui.streaks.StreaksScreen
import com.example.doora.ui.util.UiFeedbackHelper

sealed class Screen(
    val route: String,
    val title: String,
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val iconResId: Int? = null
) {
    object Home : Screen("home", "Tasks", iconVector = Icons.Default.Home)
    object Calendar : Screen("calendar", "Calendar", iconVector = Icons.Default.DateRange)
    object Statistics : Screen("statistics", "Insights", iconVector = Icons.AutoMirrored.Filled.List)
    object Streaks : Screen("streaks", "Streaks", iconResId = R.drawable.ic_fire)
}

@Composable
fun DooraNavGraph(
    navController: NavHostController,
    repository: TaskRepository
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavScreens = listOf(Screen.Home, Screen.Calendar, Screen.Statistics, Screen.Streaks)
    val showBottomBar = currentRoute in bottomNavScreens.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = com.example.doora.ui.theme.LuxuryBottomNav,
                    tonalElevation = 0.dp
                ) {
                    bottomNavScreens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    UiFeedbackHelper.playNavigationFeedback(context)
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (screen.iconResId != null) {
                                    Icon(
                                        painter = painterResource(id = screen.iconResId),
                                        contentDescription = screen.title
                                    )
                                } else if (screen.iconVector != null) {
                                    Icon(
                                        imageVector = screen.iconVector,
                                        contentDescription = screen.title
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = com.example.doora.ui.theme.GoldPrimary,
                                selectedTextColor = com.example.doora.ui.theme.GoldPrimary,
                                indicatorColor = Color(0x38393345),
                                unselectedIconColor = com.example.doora.ui.theme.TextSecondaryDark.copy(alpha = 0.75f),
                                unselectedTextColor = com.example.doora.ui.theme.TextSecondaryDark.copy(alpha = 0.75f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(150)) }
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

            composable(Screen.Streaks.route) {
                StreaksScreen(
                    repository = repository,
                    onNavigateToAddTask = { navController.navigate("add_task?recurrence=DAILY") }
                )
            }

            composable(
                route = "add_task?recurrence={recurrence}",
                arguments = listOf(
                    navArgument("recurrence") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(280)
                    ) + fadeIn(tween(200))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(240)
                    ) + fadeOut(tween(150))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(240)
                    ) + fadeOut(tween(150))
                }
            ) { backStackEntry ->
                val recurrenceArg = backStackEntry.arguments?.getString("recurrence")
                val initialRecurrence = when (recurrenceArg) {
                    "DAILY", "EVERY_DAY" -> RecurrenceType.DAILY
                    else -> null
                }
                AddEditTaskScreen(
                    repository = repository,
                    occurrenceId = null,
                    initialRecurrence = initialRecurrence,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "edit_task/{occurrenceId}",
                arguments = listOf(navArgument("occurrenceId") { type = NavType.LongType }),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(280)
                    ) + fadeIn(tween(200))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(240)
                    ) + fadeOut(tween(150))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(240)
                    ) + fadeOut(tween(150))
                }
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
