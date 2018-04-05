'10 level ADC for entering phone number

main:
	do
	  	readadc C.1, b0
	  	let b1 = b0 / 25
		let outpinsB = b1
		pause 16
	loop
	stop
