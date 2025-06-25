package com.admoai.sample.ui.screens.previews

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.components.AdCard
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.model.PlacementItem
import kotlinx.coroutines.launch

/**
 * Menu placement preview screen
 *
 * Features:
 * - Side drawer menu with navigation items
 * - Ad banner at the footer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuPreviewScreen(
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAdClick: (AdData) -> Unit = {},
    onTrackEvent: (String, String) -> Unit = {_, _ -> }
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var isCardVisible by remember { mutableStateOf(true) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Animation values for card refresh effect
    val cardAlpha by animateFloatAsState(
        targetValue = if (isCardVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 300),
        label = "card_alpha"
    )

    // Handle refresh animation and observe loading state
    LaunchedEffect(isRefreshing, isLoading) {
        if (isRefreshing) {
            // Hide the card first
            isCardVisible = false
            // Wait for the card to animate out
            kotlinx.coroutines.delay(300)
            // Trigger ad request via callback
            onRefreshClick()
            // Don't show card until loading completes (handled by next condition)
        } else if (!isLoading && !isCardVisible) {
            // Wait a moment for animation smoothness after loading completes
            kotlinx.coroutines.delay(300)
            // Show the card with the new data
            isCardVisible = true
        }
    }
    
    // Reset isRefreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    // Side drawer with navigation drawer content
    ModalNavigationDrawer(
        drawerContent = {
            MenuDrawerContent()
        },
        drawerState = drawerState,
    ) {
        Scaffold(
            topBar = {
                // Custom top bar with menu icon
                TopAppBar(
                    title = {
                        Text(
                            "Our Restaurant",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open menu"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Shopping cart logic */ }) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Shopping cart"
                            )
                        }
                    }
                )
            },
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Menu content - side drawer style menu
                    SideMenuContent()
                    
                    // Ad banner at the bottom
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                            .graphicsLayer(alpha = cardAlpha)
                    ) {
                        AdCard(
                            adData = adData,
                            placementKey = placement.key, // Add placement key
                            onAdClick = { clickedAdData -> 
                                onAdClick(clickedAdData)
                            },
                            onTrackClick = { url ->
                                // Track click events
                                onTrackEvent("click", url)
                            },
                            onTrackImpression = { url ->
                                onTrackEvent("impression", url)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        )
    }

    // Navigation bar overlay (for preview mode controls)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp) // Space for the ad card
    ) {
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            PreviewNavigationBar(
                placement = placement,
                onBackClick = onBackClick,
                onDetailsClick = onDetailsClick,
                onRefreshClick = { isRefreshing = true },
                isRefreshing = isRefreshing
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuDrawerContent() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            "Filters",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        val categories = listOf(
            "All Items",
            "Appetizers",
            "Main Courses",
            "Beverages",
            "Desserts"
        )
        
        categories.forEach { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = category == "All Items",
                    onClick = { /* Select category */ }
                )
                Text(
                    text = category,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            "Price Range",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Slider(
            value = 0.7f,
            onValueChange = { /* Update price range */ },
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun SideMenuContent() {
    // List of menu items for lateral side drawer
    val menuItems = listOf(
        "Account Profile",
        "Settings",
        "Favorites",
        "Order History",
        "Payment Methods",
        "Customer Support",
        "About"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 90.dp) // Space for ad at bottom
    ) {
        // Add some spacing at the top
        Spacer(modifier = Modifier.height(16.dp))
        
        // Create the menu items
        menuItems.forEachIndexed { index, item ->
            SideMenuItem(item)
            
            // Add divider between items (except after the last item)
            if (index < menuItems.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 64.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun SideMenuItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circle avatar/placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            // Empty circle as a placeholder
        }
        
        // Menu item text
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        )
        
        // Chevron/arrow right icon
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun MenuContentList() {
    val menuItems = listOf(
        "Appetizers" to listOf(
            MenuItem("Bruschetta", "$8.99", "Toasted bread with tomatoes, garlic, and basil"),
            MenuItem("Spinach Artichoke Dip", "$10.99", "Creamy dip with spinach and artichokes"),
            MenuItem("Calamari", "$12.99", "Fried squid with lemon aioli")
        ),
        "Main Courses" to listOf(
            MenuItem("Pasta Carbonara", "$15.99", "Classic pasta with eggs, cheese, and pancetta"),
            MenuItem("Grilled Salmon", "$22.99", "Served with roasted vegetables and herb butter"),
            MenuItem("Steak Frites", "$24.99", "Sirloin steak with house-cut fries")
        ),
        "Desserts" to listOf(
            MenuItem("Tiramisu", "$8.99", "Coffee-flavored Italian dessert"),
            MenuItem("Cheesecake", "$7.99", "New York style with berry compote")
        )
    )
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 90.dp) // Space for ad at bottom
    ) {
        menuItems.forEach { (category, items) ->
            // Category header
            item {
                Text(
                    text = category,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // Menu items for this category
            items.forEach { menuItem ->
                item {
                    MenuItemCard(menuItem)
                }
            }
        }
    }
}

@Composable
private fun MenuItemCard(menuItem: MenuItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = menuItem.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = menuItem.price,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = menuItem.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private data class MenuItem(
    val name: String,
    val price: String,
    val description: String
)
