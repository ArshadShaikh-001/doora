package com.example.doora.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.doora.data.repository.TaskRepository
import com.example.doora.ui.addtask.AddEditTaskScreen
import com.example.doora.ui.home.HomeScreen

@Composable
fun DooraNavGraph(
    navController: NavHostController,
    repository: TaskRepository
) {
    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                repository = repository,
                onAddTask = { navController.navigate("add_task") },
                onEditTask = { occurrenceId -> navController.navigate("edit_task/$occurrenceId") }
            )
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
