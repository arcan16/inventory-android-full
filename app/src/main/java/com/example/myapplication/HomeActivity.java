package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Pantalla de inicio (y de arranque): la app es local, no hay login. */
public class HomeActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        findViewById(R.id.tileInventories).setOnClickListener(v ->
                startActivity(new Intent(this, InventoriesActivity.class)));
        findViewById(R.id.tileReports).setOnClickListener(v ->
                startActivity(new Intent(this, ReportsActivity.class)));
        findViewById(R.id.tileProducts).setOnClickListener(v ->
                startActivity(new Intent(this, ProductsActivity.class)));
        findViewById(R.id.tileStock).setOnClickListener(v ->
                startActivity(new Intent(this, StockActivity.class)));
        findViewById(R.id.tileBackup).setOnClickListener(v ->
                startActivity(new Intent(this, DataTransferActivity.class)));

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        moveBottomInsetToNavBar(bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_reports) {
                startActivity(new Intent(this, ReportsActivity.class));
                return true;
            } else if (id == R.id.nav_products) {
                startActivity(new Intent(this, ProductsActivity.class));
                return true;
            } else if (id == R.id.nav_stock) {
                startActivity(new Intent(this, StockActivity.class));
                return true;
            }
            return false;
        });
    }

    /**
     * BaseActivity le pone el padding inferior del gesture bar al contenedor
     * raiz completo, lo que empuja el BottomNavigationView hacia arriba y deja
     * un hueco del color de fondo debajo de el. Aqui se mueve ese padding del
     * contenedor raiz hacia el propio BottomNavigationView para que quede
     * pegado al borde inferior real de la pantalla.
     */
    private void moveBottomInsetToNavBar(View bottomNav) {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            bottomNav.setPadding(bottomNav.getPaddingLeft(), bottomNav.getPaddingTop(),
                    bottomNav.getPaddingRight(), systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
