CreateObject("Shell.Application").ShellExecute CreateObject("Scripting.FileSystemObject").GetFile(WScript.ScriptFullName).ParentFolder.Path&"\manager.bat","uninstall",,"runas",1