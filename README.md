# Modified CANTO

This builds upon the existing [CANTO framework](https://github.com/cloud-and-smart-labs/akka-framework-for-dist-ml) by introducing an alternative optimizer for asynchronous sgd which is ADMM (alternating direction method of multipliers) which is expected to work optimally in a distributed environment. This is implemented as reference to the [Training Neural Networks Without Gradients: A Scalable ADMM Approach paper](https://arxiv.org/pdf/1605.02026.pdf).

Check out the detailed description [here](https://docs.google.com/document/d/1yDxX_yxwTog26KZt9qQ7LrJpgWsx3tLlmGDSN-DtDEU/edit?usp=sharing) and the slides associated [here](https://docs.google.com/presentation/d/1PGukKaqpTya4qCLgW76giq4nwKXpYp3XMjHw53Sv0FA/edit?usp=sharing).

# Setup

To run a minimalistic locally working CANTO:
- `./deploy.sh`
Note: Check logs of containers to view the output
