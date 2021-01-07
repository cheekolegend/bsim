$path = $pwd.path
get-childitem *.java | foreach { rename-item $_ $_.Name.Replace("GrowTest", "SingleCellChemostat") }
