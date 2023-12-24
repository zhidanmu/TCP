@echo off

for /f "delims= skip=1" %%i in ('certUtil -hashfile recvData.txt MD5') do (
	set recv=%%i
	goto next
)
:next
for /f "delims= skip=1" %%i in ('certUtil -hashfile recvData_standard.txt MD5') do (
	set standard=%%i
	goto compare
)
:compare
echo r:%recv%
echo s:%standard%
if %recv%==%standard% (echo same) else (echo error)

pause 