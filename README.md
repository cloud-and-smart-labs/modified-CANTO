# Modified CANTO

This builds upon the existing [CANTO framework](https://github.com/cloud-and-smart-labs/akka-framework-for-dist-ml) by introducing an alternative optimizer for asynchronous sgd which is ADMM (alternating direction method of multipliers) which is expected to work optimally in a distributed environment. This is implemented as reference to the [Training Neural Networks Without Gradients: A Scalable ADMM Approach paper](https://arxiv.org/pdf/1605.02026.pdf).

# Setup

To run a minimalistic locally working CANTO:
- `mvn compile`
Then parallelly (in different shells) run:  
- `mvn exec:java -Dexec.mainClass="main.Main" -Dexec.args="master 2550"`
- `mvn exec:java -Dexec.mainClass="main.Main" -Dexec.args="worker 2552"`
- `mvn exec:java -Dexec.mainClass="main.Main" -Dexec.args="worker 2534"`
