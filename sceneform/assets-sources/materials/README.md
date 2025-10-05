Download from **https://github.com/google/filament/releases** your OS specific binaries. The version must be the same as defined in the **libs.versions.toml**.
Unpack the binaries and copy the **matc** executable into the material folder. After all materials are compiled against the latest version of filament **matc** must be deleted from the materials folder.

Open a terminal and navigate to the material folder and execute for every **.mat** file the following command

`./matc -S -p mobile -o [outputname].filamat [inputname].mat`

More information to **matc** can be found here **https://google.github.io/filament/Materials.html** Section 5 **Compiling materials** 

